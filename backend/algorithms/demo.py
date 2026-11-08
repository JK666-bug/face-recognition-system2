"""
YOLOv8 face detection demo.
"""

import os
os.environ["TF_USE_LEGACY_KERAS"] = "1"
import argparse
import sys
from pathlib import Path
from face_detection import FaceDetector
from feature_extraction import FaceFeatureExtractor
import uuid
# from minio_uploader import uploader  # uploads go through RabbitMQ


def main():
    parser = argparse.ArgumentParser(description='YOLOv8 Face Detection & Tracking Unified Demo')
    
    parser.add_argument('--source', type=str, default='0', help='Input: 0 for a camera, or a video file path')
    parser.add_argument('--model', type=str, default=None, help='Face detector weights (auto-detected by default)')
    parser.add_argument('--location', type=str, default='Unknown', help='Location stored with each capture')
    
    # Default confidence comes from the shared settings file when it exists.
    default_conf = 0.65
    try:
        config_path = Path(__file__).resolve().parent.parent.parent / "config" / "settings.json"
        if config_path.exists():
            import json
            with open(config_path, 'r', encoding='utf-8') as f:
                config_data = json.load(f)
                if 'captureConfidence' in config_data:
                    default_conf = float(config_data['captureConfidence'])
    except Exception:
        pass

    parser.add_argument('--conf', type=float, default=default_conf, help=f'Detection confidence (current default: {default_conf})')
    parser.add_argument('--save', action='store_true', help='Save the annotated video')
    parser.add_argument('--output', type=str, default=None, help='Output video path')
    parser.add_argument('--no-display', action='store_true', help='Hide the preview window')
    parser.add_argument('--no-save-faces', action='store_true', help='Do not write face crops under images/')

    args = parser.parse_args()

    from backend_pusher import pusher

    print("\n" + "="*55)
    print("YOLOv8 live face tracking")
    print("="*55)
    
    detector = FaceDetector(
        model_path=args.model,
        confidence_threshold=args.conf
    )
    
    extractor = FaceFeatureExtractor(model_name="Facenet512")
    
    # Short id for this run. The playback object name is "{id}.mp4".
    current_video_session_id = str(uuid.uuid4())[:8]
    
    def print_features_callback(face_img, image_path, video_id=None, quality_score=None, capture_reason=None):
        """Extract an embedding for a new crop and publish it."""
        print(f"Extracting features for capture: {Path(image_path).name}")
        # Pass the image array so a non-ASCII path cannot break the reader.
        embedding = extractor.extract_features(face_img)
        if embedding:
            print(f"Feature extraction succeeded. Dimensions: {len(embedding)}")
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
        print(f"Processing still image: {source}")
        faces = detector.detect_image(source, save_result=args.save, output_path=args.output)
        
        if faces and args.save:
            pass
            
        print(f"Done. Found {len(faces)} face(s) in the image.")
    else:
        print(f"Video source: {source}")
        print(f"Save face crops: {'on' if not args.no_save_faces else 'off'}")
        
        stats = detector.detect_video(
            source=source,
            save_result=args.save or True,  # Always keep a video so the console can play it back.
            output_path=args.output,
            save_unique_faces=not args.no_save_faces,
            display=not args.no_display,
            feature_extractor_callback=print_features_callback,
            video_id=f"{current_video_session_id}.mp4"
        )
        
        print("\n" + "="*55)
        print("Finished.")
        print(f"Frames processed: {stats['total_frames']}")
        print(f"Distinct faces: {stats['unique_faces_found']}")
        if not args.no_save_faces:
            print(f"Face crops saved to: {stats['output_dir']}")
        
        if 'output_path' in stats and os.path.exists(stats['output_path']):
             print("Publishing the playback video through the queue...")
             pusher.push_video_data(stats['output_path'], video_id=f"{current_video_session_id}.mp4")
             
        print("="*55 + "\n")


if __name__ == "__main__":
    main()
