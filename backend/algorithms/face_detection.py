"""
YOLOv8 face detection core module
Uses YOLOv8 for precise face tracking and capture
"""

import cv2
import numpy as np
from ultralytics import YOLO
from typing import List, Dict, Union, Optional
from pathlib import Path
import utils
import os
import time


class FaceDetector:
    """
    Dedicated YOLOv8 face detector
    """
    
    def __init__(self, 
                 model_path: Optional[str] = None,
                 confidence_threshold: float = 0.5,
                 device: Optional[str] = None):
        """Initialize detector."""
        self.confidence_threshold = confidence_threshold
        self.device = device
        
        # Resolve absolute path of current script
        self.base_dir = Path(__file__).resolve().parent

        # Auto-detect runtime device
        if device is None:
            try:
                import torch
                self.device = '0' if torch.cuda.is_available() else 'cpu'
            except:
                self.device = 'cpu'
        else:
            self.device = device
        
        if model_path is None:
            model_path = str(self.base_dir / "models" / "yolov8n-face.pt")
        
        # Validate model file existence
        if not os.path.exists(model_path):
            print(f"WARNING: Face model not found: {model_path}")
            # Fallback to generic YOLO model
            model_path = "yolov8n.pt" 
            print(f"Using fallback generic model: {model_path}")
        
        print(f"Loading model: {model_path} (Device: {self.device})")
        self.model = YOLO(model_path)
        
        # Move model to selected device
        if self.device != 'cpu':
             self.model.to('cuda' if self.device == '0' else self.device)
        
        self.is_face_model = 'face' in str(model_path).lower()
        print(f"Detector ready. Mode: {'Face detection' if self.is_face_model else 'Generic object detection'}")

    def detect_image(self, 
                      image_source: Union[str, np.ndarray],
                      save_result: bool = False,
                      output_path: Optional[str] = None) -> List[Dict]:
        """Detect faces in image."""
        if isinstance(image_source, str):
            image = cv2.imread(image_source)
            if image is None:
                raise ValueError(f"Cannot read image: {image_source}")
        else:
            image = image_source
        
        # Run YOLO prediction
        results = self.model.predict(
            source=image,
            conf=self.confidence_threshold,
            verbose=False
        )
        
        # Parse detection results
        faces = utils.parse_yolo_results(results, is_face_model=self.is_face_model)
        
        if save_result and len(faces) > 0:
            res_img = utils.draw_faces(image, faces)
            if output_path is None:
                output_path = str(self.base_dir / "output" / "images" / "detection_result.jpg")
            
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            cv2.imwrite(output_path, res_img)
        
        return faces

    def detect_video(self,
                     source: Union[str, int] = 0,
                     save_result: bool = False,
                     output_path: Optional[str] = None,
                     save_unique_faces: bool = True,
                     display: bool = True,
                     feature_extractor_callback: Optional[callable] = None,
                     video_id: Optional[str] = None) -> Dict:
        """Detection, tracking and deduplication for video streams."""
        # Run tracker
        results = self.model.track(
            source=source,
            conf=self.confidence_threshold,
            device=self.device, # 使用检测到的设备 (CPU 或 GPU)
            stream=True,
            persist=True,  
            verbose=False
        )
        
        total_frames = 0
        video_writer = None
        
        # Core dedup and push logic
        capturing_candidates = {} # ID -> {last_captured_time, last_frame, last_bbox, last_seen, last_conf, best_quality_conf}
        known_faces_data = [] 
        
        # Define push callback
        face_counter = [1]
        def push_face(tid, face_frame, face_bbox, conf, reason="TRACE"):
            best_face_img = utils.crop_face(face_frame, face_bbox)
            save_path = None
            if save_unique_faces:
                images_dir.mkdir(parents=True, exist_ok=True)
                save_paths = utils.save_faces(
                    face_frame,
                    [{'bbox': face_bbox}],
                    images_dir,
                    video_id=current_video_id,
                    face_id=face_counter[0]
                )
                if save_paths:
                    save_path = save_paths[0]

            # Keep feature extraction / MQ push independent from disk saving
            if feature_extractor_callback:
                path_for_cb = save_path if save_path else str(
                    images_dir / f"_stream_{current_video_id}_tid{tid}_{reason}_{face_counter[0]}.jpg"
                )
                feature_extractor_callback(
                    best_face_img,
                    path_for_cb,
                    video_id=video_id,
                    quality_score=float(conf),
                    capture_reason=reason
                )
                known_faces_data.append({'id': tid, 'reason': reason})
                print(f"[{reason}] Capture pushed [ID: {tid}] (confidence: {conf:.2f}) (sequence: {face_counter[0]})")
                face_counter[0] += 1

        # Standard output directory
        images_dir = self.base_dir / "output" / "images"
        if save_unique_faces:
            images_dir.mkdir(parents=True, exist_ok=True)
            
        # Get current video id
        current_video_id = utils.get_next_video_id(images_dir)

        print(f"Starting detection and tracking from source {source}... (realtime + periodic + leave capture)")
        
        window_name = 'Face Tracking & Deduplication (Unified Demo)'
        if display:
            cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

        try:
            try:
                for result in results:
                    total_frames += 1
                    frame = result.orig_img
                    boxes = result.boxes
                    
                    # IDs observed in this frame
                    current_frame_ids = set()
                    current_frame_faces = []
                    
                    # If detections with track IDs are available
                    if boxes is not None and boxes.id is not None:
                        ids = boxes.id.int().cpu().tolist()
                        coords = boxes.xyxy.int().cpu().tolist()
                        confs = boxes.conf.cpu().tolist()
                        # Extract landmarks for face orientation quality check
                        kpts = result.keypoints.xy.cpu().numpy() if result.keypoints is not None else []
                        
                        current_time = time.time()
                        for i, track_id in enumerate(ids):
                            bbox = coords[i]
                            conf = confs[i]
                            # Compute yaw score
                            face_kpt = kpts[i] if len(kpts) > i else None
                            yaw_score = utils.calculate_face_orientation(face_kpt)
                            is_good_frontal = yaw_score < 0.20 # high quality frontal face
                            
                            # Record observed track id
                            current_frame_ids.add(track_id)
                            
                            # Decision logic
                            if track_id not in capturing_candidates:
                                # 1) ENTER capture
                                capturing_candidates[track_id] = {
                                    'last_captured_time': current_time,
                                    'last_frame': frame.copy(),
                                    'last_bbox': bbox,
                                    'last_seen': total_frames,
                                    'last_conf': conf,
                                    'best_quality_conf': 0.0,
                                    'has_pushed_quality': False
                                }
                                push_face(track_id, frame, bbox, conf, "ENTER")
                            else:
                                cand = capturing_candidates[track_id]
                                cand['last_seen'] = total_frames
                                cand['last_frame'] = frame.copy()
                                cand['last_bbox'] = bbox
                                cand['last_conf'] = conf
                                
                                # 2) QUALITY capture
                                if is_good_frontal:
                                    # Push first high-quality face or significantly better quality
                                    if not cand['has_pushed_quality'] or conf >= (cand['best_quality_conf'] + 0.10):
                                        push_face(track_id, frame, bbox, conf, "QUALITY")
                                        cand['has_pushed_quality'] = True
                                        cand['best_quality_conf'] = conf
                                        cand['last_captured_time'] = current_time 
                                        
                                # 3) STAYING capture every 60s
                                if current_time - cand['last_captured_time'] >= 60:
                                    push_face(track_id, frame, bbox, conf, "STAYING")
                                    cand['last_captured_time'] = current_time
    
                            # Build overlay metadata
                            current_frame_faces.append({
                                'bbox': bbox,
                                'id': track_id,
                                'status': 'TRACKING',
                                'conf': conf
                            })
    
                    # 4) LEAVE capture for disappeared tracks
                    track_ids_to_remove = []
                    for tid, cand in capturing_candidates.items():
                        # Track considered gone if absent for > 30 frames
                        if tid not in current_frame_ids and (total_frames - cand['last_seen']) > 30:
                            track_ids_to_remove.append(tid)
    
                    for tid in track_ids_to_remove:
                        cand = capturing_candidates[tid]
                        # Final leave capture
                        push_face(tid, cand['last_frame'], cand['last_bbox'], cand['last_conf'], "LEAVE")
                        del capturing_candidates[tid]
                    
                    # Draw overlays
                    annotated_frame = frame.copy()
                    for face in current_frame_faces:
                        x1, y1, x2, y2 = face['bbox']
                        # Blue box for tracked targets
                        color = (255, 100, 0)
                        cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
                        label = f"ID:{face['id']} {face['conf']:.2f}"
                        cv2.putText(annotated_frame, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
                    
                    # Display
                    if display:
                        cv2.imshow(window_name, annotated_frame)
                        if cv2.waitKey(1) & 0xFF == ord('q'):
                            break
                    
                    # Save video frame
                    if save_result:
                        if video_writer is None:
                            if output_path is None:
                                # Consistent output naming: video_N.mp4
                                output_path = str(self.base_dir / "output" / "video" / f"video_{current_video_id}.mp4")
                            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
                            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
                            h, w = annotated_frame.shape[:2]
                            video_writer = cv2.VideoWriter(output_path, fourcc, 25, (w, h))
                        video_writer.write(annotated_frame)
                        
                    if total_frames % 50 == 0:
                        print(f"Processing frame: {total_frames} | Unique captures: {len(known_faces_data)}")
            
            except KeyboardInterrupt:
                print("\nForced interruption detected (Ctrl+C), safely stopping and finalizing video...")

            # Final cleanup
            for track_id, cand in capturing_candidates.items():
                push_face(track_id, cand['last_frame'], cand['last_bbox'], cand['last_conf'], "LEAVE")
            capturing_candidates.clear()
                    
        finally:
            if display:
                cv2.destroyAllWindows()
            if video_writer:
                video_writer.release()
                print(f"Processed video saved to: {output_path}")
                
        return {
            'total_frames': total_frames,
            'unique_faces_found': len(known_faces_data),
            'output_dir': images_dir,
            'output_path': output_path
        }
