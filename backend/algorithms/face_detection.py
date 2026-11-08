"""
YOLOv8 face detector.
Detection uses YOLOv8 only.
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
    YOLOv8 face detector
    """
    
    def __init__(self, 
                 model_path: Optional[str] = None,
                 confidence_threshold: float = 0.5,
                 device: Optional[str] = None):
        """
        Create the detector
        """
        self.confidence_threshold = confidence_threshold
        self.device = device
        
        # Resolve paths from this file so relative paths stay stable
        self.base_dir = Path(__file__).resolve().parent

        # Pick CPU or CUDA when the caller did not
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
        
        # Fall back when the dedicated face weights are missing
        if not os.path.exists(model_path):
            print(f"Warning: dedicated weights not found: {model_path}")
            # Fall back to generic yolov8n.pt in the working directory
            model_path = "yolov8n.pt" 
            print(f"Using generic model for now: {model_path}")
        
        print(f"Loading model: {model_path} (Device: {self.device})")
        self.model = YOLO(model_path)
        
        # Move the model onto the selected device
        if self.device != 'cpu':
             self.model.to('cuda' if self.device == '0' else self.device)
        
        self.is_face_model = 'face' in str(model_path).lower()
        print(f"Detector ready. Mode: {'face' if self.is_face_model else 'generic object'}")

    def detect_image(self, 
                      image_source: Union[str, np.ndarray],
                      save_result: bool = False,
                      output_path: Optional[str] = None) -> List[Dict]:
        """Detect faces in one image."""
        if isinstance(image_source, str):
            image = cv2.imread(image_source)
            if image is None:
                raise ValueError(f"Cannot read image: {image_source}")
        else:
            image = image_source
        
        # Run YOLO
        results = self.model.predict(
            source=image,
            conf=self.confidence_threshold,
            verbose=False
        )
        
        # Parse boxes
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
        """
        Detect, track, and publish faces from a video or camera
        """
        # Track across frames
        results = self.model.track(
            source=source,
            conf=self.confidence_threshold,
            device=self.device,  # cpu or cuda
            stream=True,
            persist=True,  
            verbose=False
        )
        
        total_frames = 0
        video_writer = None
        
        # Publish on enter, quality, stay, and leave
        capturing_candidates = {} # ID -> {last_captured_time, last_frame, last_bbox, last_seen, last_conf, best_quality_conf}
        known_faces_data = [] 
        
        # Closure that crops a face and publishes it
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

            # Publishing does not depend on saving a file. The callback must run or the backend never sees the capture.
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
                print(f"[{reason}] published track {tid} (confidence: {conf:.2f}) (seq: {face_counter[0]})")
                face_counter[0] += 1

        # Face crops always go under algorithms/output/images
        images_dir = self.base_dir / "output" / "images"
        if save_unique_faces:
            images_dir.mkdir(parents=True, exist_ok=True)
            
        # Next video number from files already in that directory
        current_video_id = utils.get_next_video_id(images_dir)

        print(f"Detecting and tracking from {source} (enter, quality, stay, leave)")
        
        window_name = 'Face Tracking & Deduplication (Unified Demo)'
        if display:
            cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

        try:
            try:
                for result in results:
                    total_frames += 1
                    frame = result.orig_img
                    boxes = result.boxes
                    
                    # Ids visible in this frame, so missing tracks can be closed
                    current_frame_ids = set()
                    current_frame_faces = []
                    
                    # Boxes with tracker ids
                    if boxes is not None and boxes.id is not None:
                        ids = boxes.id.int().cpu().tolist()
                        coords = boxes.xyxy.int().cpu().tolist()
                        confs = boxes.conf.cpu().tolist()
                        # Keypoints used for the yaw score
                        kpts = result.keypoints.xy.cpu().numpy() if result.keypoints is not None else []
                        
                        current_time = time.time()
                        for i, track_id in enumerate(ids):
                            bbox = coords[i]
                            conf = confs[i]
                            # Yaw score
                            face_kpt = kpts[i] if len(kpts) > i else None
                            yaw_score = utils.calculate_face_orientation(face_kpt)
                            is_good_frontal = yaw_score < 0.20  # roughly within 30 degrees of frontal
                            
                            # Remember every id in this frame
                            current_frame_ids.add(track_id)
                            
                            # --- capture rules ---
                            if track_id not in capturing_candidates:
                                # 1. ENTER: publish the first time this track is seen
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
                                
                                # 2. QUALITY: a frontal face, or one at least 0.10 more confident than the last quality capture
                                if is_good_frontal:
                                    # First frontal view of this track, or confidence at least 0.10 above the last QUALITY push
                                    if not cand['has_pushed_quality'] or conf >= (cand['best_quality_conf'] + 0.10):
                                        push_face(track_id, frame, bbox, conf, "QUALITY")
                                        cand['has_pushed_quality'] = True
                                        cand['best_quality_conf'] = conf
                                        cand['last_captured_time'] = current_time 
                                        
                                # 3. STAYING: publish again after 60 seconds
                                if current_time - cand['last_captured_time'] >= 60:
                                    push_face(track_id, frame, bbox, conf, "STAYING")
                                    cand['last_captured_time'] = current_time
    
                            # Boxes drawn on this frame
                            current_frame_faces.append({
                                'bbox': bbox,
                                'id': track_id,
                                'status': 'TRACKING',
                                'conf': conf
                            })
    
                    # --- leave ---
                    # A track missing for long enough is closed on its last frame
                    track_ids_to_remove = []
                    for tid, cand in capturing_candidates.items():
                        # Missing from this frame and gone for more than 30 frames (about 1.2 seconds)
                        if tid not in current_frame_ids and (total_frames - cand['last_seen']) > 30:
                            track_ids_to_remove.append(tid)
    
                    for tid in track_ids_to_remove:
                        cand = capturing_candidates[tid]
                        # 4. LEAVE: publish the last frame of the track
                        push_face(tid, cand['last_frame'], cand['last_bbox'], cand['last_conf'], "LEAVE")
                        del capturing_candidates[tid]
                    
                    # --- draw ---
                    annotated_frame = frame.copy()
                    for face in current_frame_faces:
                        x1, y1, x2, y2 = face['bbox']
                        # Blue box while the track is live
                        color = (255, 100, 0)
                        cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
                        label = f"ID:{face['id']} {face['conf']:.2f}"
                        cv2.putText(annotated_frame, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
                    
                    # Show the window
                    if display:
                        # WINDOW_NORMAL lets a large frame fit the screen
                        cv2.imshow(window_name, annotated_frame)
                        if cv2.waitKey(1) & 0xFF == ord('q'):
                            break
                    
                    # Write the annotated frame
                    if save_result:
                        if video_writer is None:
                            if output_path is None:
                                # Filename: video_N.mp4
                                output_path = str(self.base_dir / "output" / "video" / f"video_{current_video_id}.mp4")
                            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
                            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
                            h, w = annotated_frame.shape[:2]
                            video_writer = cv2.VideoWriter(output_path, fourcc, 25, (w, h))
                        video_writer.write(annotated_frame)
                        
                    if total_frames % 50 == 0:
                        print(f"Frame {total_frames} | captures published: {len(known_faces_data)}")
            
            except KeyboardInterrupt:
                print("\nInterrupted. Stopping and saving the video...")

            # --- flush tracks still open ---
            for track_id, cand in capturing_candidates.items():
                push_face(track_id, cand['last_frame'], cand['last_bbox'], cand['last_conf'], "LEAVE")
            capturing_candidates.clear()
                    
        finally:
            if display:
                cv2.destroyAllWindows()
            if video_writer:
                video_writer.release()
                print(f"Annotated video saved: {output_path}")
                
        return {
            'total_frames': total_frames,
            'unique_faces_found': len(known_faces_data),
            'output_dir': images_dir,
            'output_path': output_path
        }
