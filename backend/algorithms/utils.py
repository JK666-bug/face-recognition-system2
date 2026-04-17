"""
辅助工具函数模块
提供人脸检测相关的辅助功能
"""

import cv2
import numpy as np
from typing import List, Dict, Tuple
import re
from pathlib import Path
import time


def get_next_video_id(directory: Path) -> int:
    """
    扫描目录，根据文件名 video_N_face_M.jpg 找到下一个可用的视频 ID (N+1)
    """
    if not directory.exists():
        return 1
    
    max_id = 0
    # 匹配文件名中的 video_(\d+)_
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
    解析 YOLOv8 检测结果
    
    Args:
        results: YOLOv8 模型的检测结果对象
        is_face_model: 是否使用专用人脸检测模型 (如果是通用模型，默认检测类别0通常为人)
        
    Returns:
        List[Dict]: 包含人脸信息的字典列表
    """
    faces = []
    
    for result in results:
        boxes = result.boxes
        for box in boxes:
            # 获取边界框坐标 (xyxy 格式)
            x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
            
            # 获取置信度
            confidence = float(box.conf[0].cpu().numpy())
            
            # 获取类别
            class_id = int(box.cls[0].cpu().numpy())
            class_name = result.names[class_id] if hasattr(result, 'names') else 'face'
            
            # 如果是专用人脸模型，直接记录
            # 如果是通用模型识别为"person"，但在本项目语境下我们追求的是专门的人脸模型
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
    """在图像上绘制人脸检测框"""
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
    """裁剪人脸区域"""
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
    保存人脸图像，使用格式: video_N_face_M.jpg
    """
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    saved_paths = []
    for i, face in enumerate(faces):
        face_img = crop_face(image, face['bbox'])
        # 使用传入的编号
        filename = output_path / f"video_{video_id}_face_{face_id + i}.jpg"
        cv2.imwrite(str(filename), face_img)
        saved_paths.append(str(filename))
    
    return saved_paths


def get_face_embedding(face_img: np.ndarray):
    """
    提取人脸特征向量 (利用 DeepFace)
    如果 DeepFace 未安装或由于环境问题（如 tf-keras 缺失）无法加载，则使用简单的直方图作为替代。
    """
    try:
        # 尝试延迟导入，因为 DeepFace 启动较慢且可能抛出兼容性异常
        from deepface import DeepFace
        # 强制不在控制台打印冗长信息
        embeddings = DeepFace.represent(
            face_img, 
            model_name="Facenet", 
            enforce_detection=False,
            detector_backend="skip"
        )
        if embeddings:
            return embeddings[0]["embedding"]
    except (ImportError, ValueError, Exception) as e:
        # 捕获所有可能的初始化错误，确保程序不崩溃
        # 仅在第一次失败时打印简短提示
        if not hasattr(get_face_embedding, "warned"):
            print(f"ℹ️  提示: 未能加载 DeepFace 增强特性 (原因: {type(e).__name__})，将使用基础特征比对...")
            get_face_embedding.warned = True
        
        # 降级方案：使用彩色直方图
        hist = cv2.calcHist([face_img], [0, 1, 2], None, [8, 8, 8], [0, 256, 0, 256, 0, 256])
        cv2.normalize(hist, hist)
        return hist.flatten()
    return None


def calculate_similarity(feat1, feat2) -> float:
    """计算两个特征向量的余弦相似度"""
    if feat1 is None or feat2 is None:
        return 0.0
    
    if isinstance(feat1, np.ndarray) and feat1.ndim > 1: # 直方图模式
        return cv2.compareHist(feat1.reshape(-1, 1), feat2.reshape(-1, 1), cv2.HISTCMP_CORREL)
        
    # 余弦相似度
    a = np.array(feat1)
    b = np.array(feat2)
    return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-6)


def calculate_face_orientation(keypoints) -> float:
    """
    根据人脸 5 个关键点计算偏转角评分 (Yaw Score)。
    数值越接近 0 表示正脸（鼻子在两眼中心），数值越大表示侧脸越严重。
    keypoints: 坐标数组，通常为 [[lx, ly], [rx, ry], [nx, ny], ...]
    返回: 偏离比率 (0.0 ~ 0.5+)
    """
    if keypoints is None or len(keypoints) < 3:
        return 1.0 # 无法判断，视为极差
    
    # 提取 x 坐标 (前三点通常是：左眼、右眼、鼻子)
    try:
        lx = keypoints[0][0] # 左眼 X
        rx = keypoints[1][0] # 右眼 X
        nx = keypoints[2][0] # 鼻子 X
        
        # 计算眼睛间距
        eye_dist = abs(rx - lx)
        if eye_dist < 1e-5: 
            return 1.0
            
        # 计算鼻子相对于左眼的水平比例，正脸时应接近 0.5
        ratio = abs(nx - lx) / eye_dist
        # 归一化偏移量：0.0 表示绝对正脸，0.5+ 表示侧脸
        yaw_score = abs(ratio - 0.5)
        return float(yaw_score)
    except Exception:
        return 1.0
