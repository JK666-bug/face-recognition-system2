"""
Face detection demo script (strict YOLOv8 mode)
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
    
    parser.add_argument('--source', type=str, default='0', help='Input source: 0 (camera) or video file path')
    parser.add_argument('--model', type=str, default=None, help='Face detection model path (auto-discovered by default)')
    parser.add_argument('--location', type=str, default='Unknown', help='Capture location tag for backend storage')
    
    # 尝试从全局配置加载默认置信度
    default_conf = 0.65
    try:
        config_path = Path(__file__).resolve().parent.parent.parent / "config" / "settings.json"
        if config_path.exists():
            import json
            with open(config_path, 'r', encoding='utf-8') as f:
                config_data = json.load(f)
                if 'captureConfidence' in config_data:
                    default_conf = float(config_data['captureConfidence'])
                    # print(f"💡 已加载全局配置置信度: {default_conf}")
    except Exception:
        pass

    parser.add_argument('--conf', type=float, default=default_conf, help=f'Detection confidence threshold (global default: {default_conf})')
    parser.add_argument('--save', action='store_true', help='Save annotated output video')
    parser.add_argument('--output', type=str, default=None, help='Output video path')
    parser.add_argument('--no-display', action='store_true', help='Hide realtime display window')
    parser.add_argument('--no-save-faces', action='store_true', help='Do not save cropped face images to images directory')

    args = parser.parse_args()

    # 按需导入推送模块
    from backend_pusher import pusher

    # Print system banner
    print("\n" + "="*55)
    print("YOLOv8 Realtime Face Tracking & Deduplication")
    print("="*55)
    
    # Initialize detector
    detector = FaceDetector(
        model_path=args.model,
        confidence_threshold=args.conf
    )
    
    # Initialize feature extractor
    extractor = FaceFeatureExtractor(model_name="Facenet512")
    
    # Generate a unique ID for current session
    current_video_session_id = str(uuid.uuid4())[:8] # 取前8位作为 ID
    
    def print_features_callback(face_img, image_path, video_id=None, quality_score=None, capture_reason=None):
        """Callback after capture: extract features and push to backend."""
        print(f"Extracting features for capture image: {Path(image_path).name}")
        # Pass face image array to avoid path encoding issues
        embedding = extractor.extract_features(face_img)
        if embedding:
            print(f"Feature extraction succeeded, dimension: {len(embedding)}")
            pusher.push_face_data(
                embedding,
                face_img,
                location=args.location,
                video_id=video_id,
                quality_score=quality_score,
                capture_reason=capture_reason
            )
        else:
            print("Feature extraction failed.")

    source = args.source
    if source.isdigit():
        source = int(source)

    if isinstance(source, str) and (source.lower().endswith(('.jpg', '.jpeg', '.png'))):
        # Image mode
        print(f"Processing static image: {source}")
        faces = detector.detect_image(source, save_result=args.save, output_path=args.output)
        
        # Static image mode can optionally run feature extraction demo
        if faces and args.save:
            # If needed, trigger feature extraction from saved image output here
            pass
            
        print(f"Done. Detected {len(faces)} face(s) in image.")
    else:
        # Video or webcam mode (with tracking)
        print(f"Video source: {source}")
        print(f"Auto capture saving: {'enabled' if not args.no_save_faces else 'disabled'}")
        
        stats = detector.detect_video(
            source=source,
            save_result=args.save or True, # keep video output enabled for replay
            output_path=args.output,
            save_unique_faces=not args.no_save_faces,
            display=not args.no_display,
            feature_extractor_callback=print_features_callback,
            video_id=f"{current_video_session_id}.mp4"
        )
        
        print("\n" + "="*55)
        print("Task completed.")
        print(f"Processed frames: {stats['total_frames']}")
        print(f"Unique tracked identities: {stats['unique_faces_found']}")
        if not args.no_save_faces:
            print(f"Saved face captures to: {stats['output_dir']}")
        
        # Push replay video to backend via MQ
        if 'output_path' in stats and os.path.exists(stats['output_path']):
             print("Pushing replay video through message queue...")
             pusher.push_video_data(stats['output_path'], video_id=f"{current_video_session_id}.mp4")
             
        print("="*55 + "\n")


if __name__ == "__main__":
    main()
