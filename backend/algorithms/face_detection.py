"""
YOLOv8 人脸检测核心模块
只使用 YOLOv8 进行精确的人脸识别
"""

import cv2
import numpy as np
from ultralytics import YOLO
from typing import List, Dict, Union, Optional
from pathlib import Path
import utils
import os


class FaceDetector:
    """
    专门的 YOLOv8 人脸检测器
    """
    
    def __init__(self, 
                 model_path: Optional[str] = None,
                 confidence_threshold: float = 0.5,
                 device: str = 'cpu'):
        """
        初始化检测器
        """
        self.confidence_threshold = confidence_threshold
        self.device = device
        
        # 获取当前脚本的绝对路径，确保相对路径在此基础上计算
        self.base_dir = Path(__file__).resolve().parent
        
        if model_path is None:
            model_path = str(self.base_dir / "models" / "yolov8n-face.pt")
        
        # 检查模型文件是否存在
        if not os.path.exists(model_path):
            print(f"⚠️  警告: 未找到专用模型文件 {model_path}")
            # 尝试在当前目录下找 yolov8n.pt 作为兜底
            model_path = "yolov8n.pt" 
            print(f"🕒 暂时使用通用模型: {model_path}")
        
        print(f"载入 YOLOv8 模型: {model_path}")
        self.model = YOLO(model_path)
        
        if device == 'cuda':
            self.model.to('cuda')
        
        self.is_face_model = 'face' in str(model_path).lower()
        print(f"检测器已就绪。模式: {'专用人脸检测' if self.is_face_model else '通用物体检测'}")

    def detect_image(self, 
                      image_source: Union[str, np.ndarray],
                      save_result: bool = False,
                      output_path: Optional[str] = None) -> List[Dict]:
        """检测图像中的人脸"""
        if isinstance(image_source, str):
            image = cv2.imread(image_source)
            if image is None:
                raise ValueError(f"无法读取图片: {image_source}")
        else:
            image = image_source
        
        # 使用 YOLO 进行预测
        results = self.model.predict(
            source=image,
            conf=self.confidence_threshold,
            verbose=False
        )
        
        # 解析结果
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
                     similarity_threshold: float = 0.6,
                     display: bool = True,
                     feature_extractor_callback: Optional[callable] = None,
                     video_id: Optional[str] = None) -> Dict:
        """
        针对视频流的检测、追踪与去重
        """
        # 轨迹追踪
        results = self.model.track(
            source=source,
            conf=self.confidence_threshold,
            stream=True,
            persist=True,  
            verbose=False
        )
        
        total_frames = 0
        video_writer = None
        
        # 核心去重数据
        processed_track_ids = set()
        known_faces_data = [] 
        
        # 标准化输出目录：始终保存在 algorithms/output/images (如用户要求)
        images_dir = self.base_dir / "output" / "images"
        if save_unique_faces:
            images_dir.mkdir(parents=True, exist_ok=True)
            
        # 获取当前视频的全局编号
        current_video_id = utils.get_next_video_id(images_dir)
        face_counter = 1

        print(f"🎥 正从 {source} 开始检测与追踪...")
        
        try:
            for result in results:
                total_frames += 1
                frame = result.orig_img
                boxes = result.boxes
                
                current_frame_faces = []
                
                # 如果检测并追踪到了物体
                if boxes is not None and boxes.id is not None:
                    ids = boxes.id.int().cpu().tolist()
                    coords = boxes.xyxy.int().cpu().tolist()
                    confs = boxes.conf.cpu().tolist()
                    
                    for i, track_id in enumerate(ids):
                        bbox = coords[i]
                        conf = confs[i]
                        
                        is_new_person = False
                        
                        # --- 核心去重逻辑 ---
                        # A. 首先检查轨迹 ID 是否已经处理过图片保存
                        if track_id not in processed_track_ids:
                            
                            # 获取当前脸部图像
                            face_img = utils.crop_face(frame, bbox)
                            embedding = utils.get_face_embedding(face_img)
                            
                            # B. 其次通过相似度检查是否为“已知人脸”（即便 ID 变了）
                            is_duplicate = False
                            best_match_id = -1
                            max_sim = 0
                            
                            for known in known_faces_data:
                                sim = utils.calculate_similarity(embedding, known['embedding'])
                                if sim > max_sim:
                                    max_sim = sim
                                    best_match_id = known['id']
                                
                                if sim > similarity_threshold:
                                    is_duplicate = True
                                    # 将当前 ID 映射到已知人脸，防止后续帧重复计算
                                    processed_track_ids.add(track_id)
                                    break
                            
                            if is_duplicate:
                                print(f"⏭️  跳过 ID: {track_id} (与 ID: {best_match_id} 相似度 {max_sim:.2f} > {similarity_threshold:.2f})")
                            
                            # C. 如果确实是新面孔，则保存
                            if not is_duplicate:
                                is_new_person = True
                                if save_unique_faces:
                                    # 使用 hierarchical 命名: video_N_face_M.jpg
                                    save_path = utils.save_faces(
                                        frame, 
                                        [{'bbox': bbox}], 
                                        images_dir, 
                                        video_id=current_video_id, 
                                        face_id=face_counter
                                    )[0]
                                                                       # 如果有特征提取回调，执行提取
                                    if feature_extractor_callback:
                                        feature_extractor_callback(face_img, save_path, video_id=video_id)
                                        
                                    known_faces_data.append({
                                        'id': track_id,
                                        'embedding': embedding,
                                        'path': save_path
                                    })
                                    print(f"✨ 识别到新面孔! [ID: {track_id}] 已保存图片: {Path(save_path).name}")
                                    face_counter += 1
                                
                                # 标记此 ID 的提取任务已完成
                                processed_track_ids.add(track_id)
                        
                        current_frame_faces.append({
                            'bbox': bbox,
                            'id': track_id,
                            'status': 'NEW' if is_new_person else 'TRACKING',
                            'conf': conf
                        })
                
                # --- 可视化 (绘制结果) ---
                annotated_frame = frame.copy()
                for face in current_frame_faces:
                    x1, y1, x2, y2 = face['bbox']
                    # 新发现的人用绿色框，追踪中的人用蓝色框
                    color = (0, 255, 0) if face['status'] == 'NEW' else (255, 100, 0)
                    cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
                    label = f"ID:{face['id']} {face['conf']:.2f}"
                    cv2.putText(annotated_frame, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
                
                # 显示
                if display:
                    cv2.imshow('Face Tracking & Deduplication (Unified Demo)', annotated_frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        break
                
                # 保存视频帧
                if save_result:
                    if video_writer is None:
                        if output_path is None:
                            # 同步文件名: video_N.mp4
                            output_path = str(self.base_dir / "output" / "video" / f"video_{current_video_id}.mp4")
                        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
                        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
                        h, w = annotated_frame.shape[:2]
                        video_writer = cv2.VideoWriter(output_path, fourcc, 25, (w, h))
                    video_writer.write(annotated_frame)
                    
                if total_frames % 50 == 0:
                    print(f"处理中: 第 {total_frames} 帧 | 已捕获独特人脸: {len(known_faces_data)}")
                    
        finally:
            if display:
                cv2.destroyAllWindows()
            if video_writer:
                video_writer.release()
                print(f"✅ 处理后的视频已保存: {output_path}")
                
        return {
            'total_frames': total_frames,
            'unique_faces_found': len(known_faces_data),
            'output_dir': images_dir,
            'output_path': output_path
        }
