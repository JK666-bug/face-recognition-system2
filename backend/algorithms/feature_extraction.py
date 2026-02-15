"""
特征提取模块 (DeepFace)
"""

import os
from pathlib import Path

# 获取 project/backend/algorithms/models 路径
# __file__ 是 .../backend/algorithms/feature_extraction.py
# parent 是 .../backend/algorithms
# models_dir 是 .../backend/algorithms/models
models_dir = str(Path(__file__).resolve().parent / "models")
os.environ["DEEPFACE_HOME"] = models_dir

os.environ["TF_USE_LEGACY_KERAS"] = "1"
import numpy as np
from deepface import DeepFace
from typing import List, Optional, Union

class FaceFeatureExtractor:
    """
    使用 DeepFace 进行人脸特征提取
    """
    
    def __init__(self, model_name: str = "Facenet512"):
        """
        初始化提取器
        
        Args:
            model_name: 模型名称 (默认 Facenet512 以获得 512 维向量)
        """
        self.model_name = model_name
        print(f"🧬  初始化特征提取模型: {model_name}")
        # 这里不预加载，在第一次运行时会自动加载

    def extract_features(self, image_source: Union[str, np.ndarray]) -> Optional[List[float]]:
        """
        从人脸图像中提取特征向量
        
        Args:
            image_source: 图像路径或 numpy 数组 (人脸裁剪图)
            
        Returns:
            Optional[List[float]]: 512 维浮点数列表，提取失败返回 None
        """
        try:
            # 提取嵌入向量
            # detector_backend="skip" 因为输入已经是裁剪好的人脸图片了
            embeddings = DeepFace.represent(
                img_path=image_source,
                model_name=self.model_name,
                enforce_detection=False,
                detector_backend="skip",
                align=True
            )
            
            if embeddings and len(embeddings) > 0:
                embedding = embeddings[0]["embedding"]
                return embedding
        except Exception as e:
            print(f"❌  特征提取失败: {e}")
            return None
        return None

def main():
    # 测试脚本
    extractor = FaceFeatureExtractor()
    print("测试特征提取...")
    # 这里可以使用一个测试图片
    
if __name__ == "__main__":
    main()
