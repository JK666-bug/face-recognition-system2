"""
Face embeddings via DeepFace.
"""

import os
from pathlib import Path

# backend/algorithms/models
models_dir = str(Path(__file__).resolve().parent / "models")
os.environ["DEEPFACE_HOME"] = models_dir

os.environ["TF_USE_LEGACY_KERAS"] = "1"
import numpy as np
from deepface import DeepFace
from typing import List, Optional, Union

class FaceFeatureExtractor:
    """Extract a face embedding with DeepFace."""
    
    def __init__(self, model_name: str = "Facenet512"):
        """
        Args:
            model_name: DeepFace model name. Facenet512 yields a 512-d vector.
        """
        self.model_name = model_name
        print(f"INFO: Initializing feature extraction model: {model_name}")
        # The model is loaded on the first call.

    def extract_features(self, image_source: Union[str, np.ndarray]) -> Optional[List[float]]:
        """
        Extract an embedding from a face image.

        Args:
            image_source: image path or a numpy array of an already cropped face

        Returns:
            A list of 512 floats, or None when extraction fails.
        """
        try:
            # detector_backend="skip" because the input is already a face crop.
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
            print(f"ERROR: Feature extraction failed: {e}")
            return None
        return None

def main():
    extractor = FaceFeatureExtractor()
    print("Feature extraction smoke test...")
    
if __name__ == "__main__":
    main()
