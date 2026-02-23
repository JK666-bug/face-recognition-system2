"""
人脸检测演示脚本 (严格 YOLOv8 模式)
"""

import os
os.environ["TF_USE_LEGACY_KERAS"] = "1"
import argparse
import sys
from pathlib import Path
from face_detection import FaceDetector
from feature_extraction import FaceFeatureExtractor
import uuid
# from minio_uploader import uploader # 不再直接上传，统一走 MQ


def main():
    parser = argparse.ArgumentParser(description='YOLOv8 Face Detection & Tracking Unified Demo')
    
    parser.add_argument('--source', type=str, default='0', help='输入源: 0 (摄像头) 或 视频文件路径')
    parser.add_argument('--model', type=str, default=None, help='专用面部检测模型路径 (默认自动查找)')
    parser.add_argument('--conf', type=float, default=0.5, help='检测置信度 (默认 0.5)')
    parser.add_argument('--sim', type=float, default=0.9, help='去重相似度阈值 (默认 0.9, 越高越不易漏抓)')
    parser.add_argument('--save', action='store_true', help='是否保存带有检测框的视频结果')
    parser.add_argument('--output', type=str, default=None, help='输出视频路径')
    parser.add_argument('--no-display', action='store_true', help='隐藏实时窗口')
    parser.add_argument('--no-save-faces', action='store_true', help='禁止保存独立的人脸图片到 images 目录')
    parser.add_argument('--location', type=str, default='Unknown', help='抓拍地点 (用于后端存入对象数据库)')

    args = parser.parse_args()

    # 按需导入推送模块
    from backend_pusher import pusher

    # 打印系统状态
    print("\n" + "="*55)
    print("🚀 YOLOv8 实时面部追踪与自动去重系统")
    print("="*55)
    
    # 初始化检测器
    detector = FaceDetector(
        model_path=args.model,
        confidence_threshold=args.conf
    )
    
    # 初始化特征提取器
    extractor = FaceFeatureExtractor(model_name="Facenet512")
    
    # 生成当前视频练习的唯一 ID
    current_video_session_id = str(uuid.uuid4())[:8] # 取前8位作为 ID
    
    def print_features_callback(face_img, image_path, video_id=None):
        """抓拍新面孔后的回调：提取并打印特征，并推送到后端"""
        print(f"🧬  正在为抓拍图片提取特征: {Path(image_path).name}")
        # 传递图片数组 face_img 避开路径中的中文问题
        embedding = extractor.extract_features(face_img)
        if embedding:
            # 1. 打印演示
            print(f"✅  特征提取成功! 维度: {len(embedding)}")
            # 2. 推送到后端 MQ
            pusher.push_face_data(embedding, face_img, location=args.location, video_id=video_id)
        else:
            print("⚠️  特征提取未能成功。")

    source = args.source
    if source.isdigit():
        source = int(source)

    if isinstance(source, str) and (source.lower().endswith(('.jpg', '.jpeg', '.png'))):
        # 图像模式
        print(f"🔍 正在处理静态图像: {source}")
        faces = detector.detect_image(source, save_result=args.save, output_path=args.output)
        
        # 静态图像也执行一次特征提取演示
        if faces and args.save:
            # 假设 detect_image 也会保存图片，我们可以对保存的结果进行提取
            # 为了简单起见，这里可以根据 detect_image 的逻辑手动调用一次
            pass
            
        print(f"✅ 完成！在图像中识别到 {len(faces)} 个人脸。")
    else:
        # 视频或摄像头模式 (含追踪与去重)
        print(f"🎬 视频源: {source}")
        print(f"⚙️  去重相似度阈值: {args.sim}")
        print(f"📸 自动抓拍保存: {'开启' if not args.no_save_faces else '关闭'}")
        
        stats = detector.detect_video(
            source=source,
            save_result=args.save or True, # 强制开启保存视频以供回放
            output_path=args.output,
            save_unique_faces=not args.no_save_faces,
            similarity_threshold=args.sim,
            display=not args.no_display,
            feature_extractor_callback=print_features_callback,
            video_id=f"{current_video_session_id}.mp4"
        )
        
        print("\n" + "="*55)
        print("✅ 任务处理完毕！")
        print(f"📊 处理帧数: {stats['total_frames']}")
        print(f"👥 独特人物识别总数: {stats['unique_faces_found']}")
        if not args.no_save_faces:
            print(f"🖼️  所有人脸抓拍照已保存至: {stats['output_dir']}")
        
        # --- 自动通过 MQ 推送视频文件 ---
        if 'output_path' in stats and os.path.exists(stats['output_path']):
             print(f"📤 正在通过消息队列推送视频回放...")
             pusher.push_video_data(stats['output_path'], video_id=f"{current_video_session_id}.mp4")
             
        print("="*55 + "\n")


if __name__ == "__main__":
    main()
