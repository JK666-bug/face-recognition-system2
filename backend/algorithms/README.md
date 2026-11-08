# Capture engine

This directory watches video. YOLOv8 tracks faces, Facenet512 builds a 512-d vector, and RabbitMQ delivers that payload to Spring Boot. How the pieces connect is documented in the repository [README](../../README.md).

## Run

From the repository root, with the virtualenv active:

```bash
python backend/algorithms/demo.py --source 0 --location "Entrance"
python backend/algorithms/demo.py --source path/to/video.mp4 --location "Main Gate"
python backend/algorithms/demo.py --source "rtsp://127.0.0.1:8554/live" --location "Main Gate" --no-display
```

The Detection Task page in the console starts the same script, once per webcam or stream URL.

| Flag | Meaning | Default |
| --- | --- | --- |
| `--source` | Camera index, video path, or RTSP/HTTP URL | `0` |
| `--location` | Location stored on the capture | `Unknown` |
| `--conf` | Detection confidence | `captureConfidence` in `config/settings.json` |
| `--model` | Detector weights | `models/yolov8n-face.pt` |
| `--no-display` | Hide the preview window | off |
| `--no-save-faces` | Skip local face crops | off. The backend still receives the capture |
| `--output` | Path of the annotated video | Chosen by the detector, then uploaded for playback |

## Script called by Java

`extract_cli.py` takes an image path. It detects faces, crops the largest one, and prints one JSON line with the embedding. People-library upload and photo search use this script. `--no-detect` embeds the whole image instead, for debugging.

## Models

`feature_extraction.py` sets `DEEPFACE_HOME` to `models/`. If `models/yolov8n-face.pt` is missing, the detector falls back to generic `yolov8n.pt`.
