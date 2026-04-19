"""
特征提取命令行工具 - 供 Java 后端调用
人物库 / 以脸搜图：先用 YOLOv8 人脸检测截最大脸，再 Facenet512 提向量；未检出则回退整图。
"""

import sys
import json
import os
from pathlib import Path

# 设置环境
os.environ["TF_USE_LEGACY_KERAS"] = "1"
os.environ["PYTHONIOENCODING"] = "utf-8"
# 确保 DeepFace 找到正确的模型路径
models_dir = str(Path(__file__).resolve().parent / "models")
os.environ["DEEPFACE_HOME"] = models_dir

try:
    from feature_extraction import FaceFeatureExtractor
except ImportError:
    sys.path.append(os.path.dirname(os.path.abspath(__file__)))
    from feature_extraction import FaceFeatureExtractor


def _ensure_algorithms_path():
    d = os.path.dirname(os.path.abspath(__file__))
    if d not in sys.path:
        sys.path.insert(0, d)


def resolve_embedding_source(image_path: str):
    """
    使用 YOLOv8 检测后裁剪面积最大的人脸，供 DeepFace 提特征。
    未检出或裁剪异常时返回原图路径字符串（与旧行为一致）。
    """
    import cv2

    _ensure_algorithms_path()
    from face_detection import FaceDetector
    from utils import crop_face

    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Cannot read image: {image_path}")

    detector = FaceDetector()
    faces = detector.detect_image(img)
    if not faces:
        return image_path

    def area(f):
        b = f["bbox"]
        return max(0, b[2] - b[0]) * max(0, b[3] - b[1])

    best = max(faces, key=area)
    crop = crop_face(img, best["bbox"])
    if crop is None or crop.size == 0:
        return image_path
    return crop


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
        sys.exit(1)

    image_path = sys.argv[1]

    if not os.path.exists(image_path):
        print(json.dumps({"error": f"Image path does not exist: {image_path}"}))
        sys.exit(1)

    # 可选：python extract_cli.py <path> --no-detect  （调试 / 对比）
    use_detect = not (len(sys.argv) > 2 and sys.argv[2] == "--no-detect")

    try:
        extractor = FaceFeatureExtractor(model_name="Facenet512")
        if use_detect:
            source = resolve_embedding_source(image_path)
        else:
            source = image_path

        embedding = extractor.extract_features(source)

        if embedding:
            print(json.dumps({"embedding": embedding}))
        else:
            print(json.dumps({"error": "Feature extraction failed"}))
            sys.exit(1)

    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)


if __name__ == "__main__":
    main()
