"""
Helpers for face detection.
"""

import cv2
import numpy as np
from typing import List, Dict, Tuple
import re
from pathlib import Path
import time


def get_next_video_id(directory: Path) -> int:
    """
    Scan video_N_face_M.jpg names and return the next video id.
    """
    if not directory.exists():
        return 1
    
    max_id = 0
    # Match video_<id>_ in the filename
    pattern = re.compile(r'video_(\d+)_face_')
    
    for file in directory.glob('*.jpg'):
        match = pattern.search(file.name)
        if match:
            video_id = int(match.group(1))
            if video_id > max_id:
                max_id = video_id
                
    return max_id + 1


def parse_yolo_results(results, is_face_model: bool = True) -> List[Dict]:
    """
    Parse YOLOv8 detection results
    
    Args:
        results: YOLOv8 result object
        is_face_model: dedicated face model. A generic model treats class person as a face.
        
    Returns:
        List[Dict]: one dict per face
    """
    faces = []
    
    for result in results:
        boxes = result.boxes
        for box in boxes:
            # Bounding box in xyxy
            x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
            
            # Confidence
            confidence = float(box.conf[0].cpu().numpy())
            
            # Class id
            class_id = int(box.cls[0].cpu().numpy())
            class_name = result.names[class_id] if hasattr(result, 'names') else 'face'
            
            # A dedicated face model keeps every box.
            # A generic model only keeps the person class.
            if is_face_model or class_name == 'person':
                faces.append({
                    'bbox': [int(x1), int(y1), int(x2), int(y2)],
                    'confidence': confidence,
                    'class': 'face'
                })
    
    return faces


def draw_faces(image: np.ndarray, faces: List[Dict], 
               color: Tuple[int, int, int] = (0, 255, 0),
               thickness: int = 2) -> np.ndarray:
    """Draw face boxes on a copy of the image."""
    img_copy = image.copy()
    
    for face in faces:
        bbox = face['bbox']
        confidence = face['confidence']
        
        x1, y1, x2, y2 = bbox
        cv2.rectangle(img_copy, (x1, y1), (x2, y2), color, thickness)
        
        label = f"Face: {confidence:.2f}"
        cv2.putText(img_copy, label, (x1, y1 - 10),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
    
    return img_copy


def crop_face(image: np.ndarray, bbox: List[int], padding: int = 20) -> np.ndarray:
    """Crop a face with padding."""
    x1, y1, x2, y2 = bbox
    h, w = image.shape[:2]
    
    x1 = max(0, x1 - padding)
    y1 = max(0, y1 - padding)
    x2 = min(w, x2 + padding)
    y2 = min(h, y2 + padding)
    
    return image[y1:y2, x1:x2]


def save_faces(image: np.ndarray, faces: List[Dict], output_dir: str, 
               video_id: int = 1, face_id: int = 1) -> List[str]:
    """
    Save crops as video_N_face_M.jpg
    """
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    saved_paths = []
    for i, face in enumerate(faces):
        face_img = crop_face(image, face['bbox'])
        # Use the caller-supplied ids
        filename = output_path / f"video_{video_id}_face_{face_id + i}.jpg"
        cv2.imwrite(str(filename), face_img)
        saved_paths.append(str(filename))
    
    return saved_paths


def get_face_embedding(face_img: np.ndarray):
    """
    Extract an embedding with DeepFace.
    If DeepFace cannot load, fall back to a color histogram.
    """
    try:
        # Import lazily. DeepFace is slow to start and may fail to load.
        from deepface import DeepFace
        # Skip extra detector logging
        embeddings = DeepFace.represent(
            face_img, 
            model_name="Facenet", 
            enforce_detection=False,
            detector_backend="skip"
        )
        if embeddings:
            return embeddings[0]["embedding"]
    except (ImportError, ValueError, Exception) as e:
        # Keep running if DeepFace fails to initialize.
        # Warn once.
        if not hasattr(get_face_embedding, "warned"):
            print(f"DeepFace is unavailable ({type(e).__name__}). Using a color histogram instead.")
            get_face_embedding.warned = True
        
        # Fallback: color histogram
        hist = cv2.calcHist([face_img], [0, 1, 2], None, [8, 8, 8], [0, 256, 0, 256, 0, 256])
        cv2.normalize(hist, hist)
        return hist.flatten()
    return None


def calculate_similarity(feat1, feat2) -> float:
    """Cosine similarity of two embeddings."""
    if feat1 is None or feat2 is None:
        return 0.0
    
    if isinstance(feat1, np.ndarray) and feat1.ndim > 1:  # histogram fallback
        return cv2.compareHist(feat1.reshape(-1, 1), feat2.reshape(-1, 1), cv2.HISTCMP_CORREL)
        
    # Cosine similarity
    a = np.array(feat1)
    b = np.array(feat2)
    return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-6)


def calculate_face_orientation(keypoints) -> float:
    """
    Yaw score from five face keypoints.
    0 is frontal (the nose sits between the eyes). Larger means a stronger profile.
    keypoints: [[lx, ly], [rx, ry], [nx, ny], ...]
    Returns an offset ratio, about 0.0 to 0.5+.
    """
    if keypoints is None or len(keypoints) < 3:
        return 1.0  # unknown pose, treat as a poor view
    
    # x coordinates. The first three points are left eye, right eye, nose.
    try:
        lx = keypoints[0][0]  # left eye x
        rx = keypoints[1][0]  # right eye x
        nx = keypoints[2][0]  # nose x
        
        # Distance between the eyes
        eye_dist = abs(rx - lx)
        if eye_dist < 1e-5: 
            return 1.0
            
        # Nose position between the eyes. Frontal faces sit near 0.5.
        ratio = abs(nx - lx) / eye_dist
        # 0.0 is frontal. 0.5 and above is a strong profile.
        yaw_score = abs(ratio - 0.5)
        return float(yaw_score)
    except Exception:
        return 1.0
