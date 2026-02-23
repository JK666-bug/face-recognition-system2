"""
特征提取命令行工具 - 供 Java 后端调用
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
    # 兼容直接从算法目录运行
    sys.path.append(os.path.dirname(os.path.abspath(__file__)))
    from feature_extraction import FaceFeatureExtractor

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
        sys.exit(1)

    image_path = sys.argv[1]
    
    if not os.path.exists(image_path):
        print(json.dumps({"error": f"Image path does not exist: {image_path}"}))
        sys.exit(1)

    try:
        extractor = FaceFeatureExtractor(model_name="Facenet512")
        embedding = extractor.extract_features(image_path)
        
        if embedding:
            # 直接输出 JSON 结果到标准输出，Java 捕获此输出
            print(json.dumps({"embedding": embedding}))
        else:
            print(json.dumps({"error": "Feature extraction failed"}))
            sys.exit(1)
            
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)

if __name__ == "__main__":
    main()
