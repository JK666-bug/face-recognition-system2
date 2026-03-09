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
                 device: Optional[str] = None):
        """
        初始化检测器
        """
        self.confidence_threshold = confidence_threshold
        self.device = device
        
        # 获取当前脚本的绝对路径，确保相对路径在此基础上计算
        self.base_dir = Path(__file__).resolve().parent

        # 自动探测设备
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
        
        # 检查模型文件是否存在
        if not os.path.exists(model_path):
            print(f"⚠️  警告: 未找到专用模型文件 {model_path}")
            # 尝试在当前目录下找 yolov8n.pt 作为兜底
            model_path = "yolov8n.pt" 
            print(f"🕒 暂时使用通用模型: {model_path}")
        
        print(f"载入模型: {model_path} (Device: {self.device})")
        self.model = YOLO(model_path)
        
        # 统一移动模型到指定设备
        if self.device != 'cpu':
             self.model.to('cuda' if self.device == '0' else self.device)
        
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
            device=self.device, # 使用检测到的设备 (CPU 或 GPU)
            stream=True,
            persist=True,  
            verbose=False
        )
        
        total_frames = 0
        video_writer = None
        
        # 核心去重数据
        processed_track_ids = set()
        capturing_candidates = {} # 暂存候选抓拍 (ID -> {best_conf, best_frame, best_bbox, frames_seen})
        known_faces_data = [] 
        
        # 标准化输出目录：始终保存在 algorithms/output/images (如用户要求)
        images_dir = self.base_dir / "output" / "images"
        if save_unique_faces:
            images_dir.mkdir(parents=True, exist_ok=True)
            
        # 获取当前视频的全局编号
        current_video_id = utils.get_next_video_id(images_dir)
        face_counter = 1

        print(f"🎥 正从 {source} 开始检测与追踪... (按 'q' 键退出)")
        
        window_name = 'Face Tracking & Deduplication (Unified Demo)'
        if display:
            cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

        try:
            for result in results:
                total_frames += 1
                frame = result.orig_img
                boxes = result.boxes
                
                # 定义这一帧看到的 ID 集合，用于后续判断哪些 ID 消失了
                current_frame_ids = set()
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
                        
                        # --- 核心逻辑：全局最优抓拍 (Global-Best Capture) ---
                        # 记录当前帧看到的所有 ID
                        current_frame_ids.add(track_id)
                        
                        # 只要是没彻底处理完的新 ID，就尝试在缓冲区中寻找“绝对最美瞬间”
                        if track_id not in processed_track_ids:
                            # 如果该 ID 不在候选区，初始化
                            if track_id not in capturing_candidates:
                                capturing_candidates[track_id] = {
                                    'best_conf': conf,
                                    'best_frame': frame.copy(),
                                    'best_bbox': bbox,
                                    'last_seen': total_frames
                                }
                            else:
                                # 持续更新“最高置信度”记录
                                cand = capturing_candidates[track_id]
                                cand['last_seen'] = total_frames
                                if conf > cand['best_conf']:
                                    cand['best_conf'] = conf
                                    cand['best_frame'] = frame.copy()
                                    cand['best_bbox'] = bbox

                        # 填充当前帧的人脸信息用于展示
                        current_frame_faces.append({
                            'bbox': bbox,
                            'id': track_id,
                            'status': 'TRACKING', # 全局最优模式下，我们不再实时标注 NEW，而是统一为追踪
                            'conf': conf
                        })

                # --- 动态收网：判断哪些轨迹已经消失 ---
                # 遍历候选区，如果某个 ID 已经很久没看到了，我们就认为他的轨迹结束了，可以抓拍了
                track_ids_to_commit = []
                for tid, cand in capturing_candidates.items():
                    # 如果该 ID 不在当前帧里，且已经消失超过了 30 帧 (约 1 秒)
                    if tid not in current_frame_ids and (total_frames - cand['last_seen']) > 30:
                        track_ids_to_commit.append(tid)

                for tid in track_ids_to_commit:
                    cand = capturing_candidates[tid]
                    best_face_img = utils.crop_face(cand['best_frame'], cand['best_bbox'])
                    
                    if save_unique_faces:
                        save_paths = utils.save_faces(
                            cand['best_frame'], 
                            [{'bbox': cand['best_bbox']}], 
                            images_dir, 
                            video_id=current_video_id, 
                            face_id=face_counter
                        )
                        if save_paths:
                            save_path = save_paths[0]
                            if feature_extractor_callback:
                                feature_extractor_callback(best_face_img, save_path, video_id=video_id)
                            known_faces_data.append({'id': tid})
                            print(f"🎬 轨迹结束！已选出全局最优瞬间 [ID: {tid}] (最高置信度: {cand['best_conf']:.2f})")
                            face_counter += 1
                    
                    processed_track_ids.add(tid)
                    del capturing_candidates[tid]
                
                # --- 可视化 (绘制结果) ---
                annotated_frame = frame.copy()
                for face in current_frame_faces:
                    x1, y1, x2, y2 = face['bbox']
                    # 统一使用蓝色框表示正在追踪
                    color = (255, 100, 0)
                    cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, 2)
                    label = f"ID:{face['id']} {face['conf']:.2f}"
                    cv2.putText(annotated_frame, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
                
                # 显示
                if display:
                    # 如果帧分辨率特别大 (比如 4K)，适当调整窗口显示
                    cv2.imshow(window_name, annotated_frame)
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
            
            # --- 最终清理：如果视频结束时还有由于帧数不足没来得及抓拍的候选人，现在强制抓拍 ---
            for track_id, cand in capturing_candidates.items():
                if track_id not in processed_track_ids:
                    best_face_img = utils.crop_face(cand['best_frame'], cand['best_bbox'])
                    if save_unique_faces:
                        save_paths = utils.save_faces(
                            cand['best_frame'], 
                            [{'bbox': cand['best_bbox']}], 
                            images_dir, 
                            video_id=current_video_id, 
                            face_id=face_counter
                        )
                        if save_paths:
                            save_path = save_paths[0]
                            if feature_extractor_callback:
                                feature_extractor_callback(best_face_img, save_path, video_id=video_id)
                            known_faces_data.append({'id': track_id})
                            print(f"🏁 视频结束补抓: [ID: {track_id}] (最高置信度: {cand['best_conf']:.2f})")
                            face_counter += 1
                    processed_track_ids.add(track_id)
            capturing_candidates.clear()
                    
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
