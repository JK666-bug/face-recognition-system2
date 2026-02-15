# YOLOv8 人脸检测系统

基于 YOLOv8 的人脸检测系统，支持图像、视频文件和实时摄像头检测。

## 功能特性

✨ **多种输入源支持**
- 📷 单张图像检测
- 🎬 视频文件检测
- 🎥 实时摄像头检测
- 🌐 网络流检测（RTSP 等）

✨ **强大的检测能力**
- 🎯 高精度人脸检测
- ⚡ 实时处理速度
- 🔧 可调节置信度阈值
- 💾 结果保存功能

✨ **人脸提取**
- 🖼️ 自动裁剪人脸区域
- 📁 批量保存人脸图像
- 🔗 为 DeepFace 集成做好准备

## 安装依赖

```bash
# 进入后端目录
cd backend

# 安装依赖
pip install -r requirements.txt
```

**注意**: 首次运行时，YOLOv8 会自动下载预训练模型（约 6MB），请确保网络连接正常。

## 快速开始

### 1. 检测图像

```bash
cd algorithms
python demo.py --source test.jpg --save
```

### 2. 检测视频文件

```bash
python demo.py --source video.mp4 --save --output output/result.mp4
```

### 3. 实时摄像头检测

```bash
python demo.py --source 0
```

按 `q` 键退出实时检测。

### 4. 提取人脸

```bash
python demo.py --source image.jpg --extract-faces
```

提取的人脸将保存到 `output/faces/` 目录。

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--source` | 输入源（图像路径/视频路径/摄像头索引） | `0` |
| `--model` | YOLOv8 模型路径 | `yolov8n.pt` |
| `--conf` | 置信度阈值 (0-1) | `0.5` |
| `--device` | 运行设备 (`cpu` 或 `cuda`) | `cpu` |
| `--save` | 保存检测结果 | - |
| `--output` | 输出文件路径 | `output/detection_result.*` |
| `--no-display` | 不显示检测结果（仅保存） | - |
| `--extract-faces` | 提取并保存人脸 | - |
| `--max-frames` | 最大处理帧数（仅视频） | `None` |

## 使用示例

### Python 代码调用

```python
from face_detection import FaceDetector

# 初始化检测器
detector = FaceDetector(
    model_path='yolov8n.pt',
    confidence_threshold=0.5,
    device='cpu'
)

# 检测图像
faces = detector.detect_image('test.jpg', save_result=True)
print(f"检测到 {len(faces)} 个人脸")

for face in faces:
    print(f"位置: {face['bbox']}, 置信度: {face['confidence']:.2f}")

# 检测视频
stats = detector.detect_video(
    source='video.mp4',
    save_result=True,
    display=True
)

# 提取人脸
saved_paths = detector.extract_faces_from_image('test.jpg')
```

## 项目结构

```
algorithms/
├── face_detection.py      # 人脸检测核心模块
├── utils.py               # 辅助工具函数
├── demo.py                # 演示脚本
├── models/                # 模型文件目录
│   └── yolov8n-face.pt   # YOLOv8 人脸检测模型（可选）
└── output/                # 输出结果目录
    ├── videos/            # 处理后的视频
    ├── images/            # 检测结果图像
    └── faces/             # 提取的人脸
```

## YOLOv8 模型选择

| 模型 | 大小 | 速度 | 精度 | 推荐场景 |
|------|------|------|------|----------|
| `yolov8n.pt` | 6MB | ⚡⚡⚡⚡⚡ | ⭐⭐⭐ | 实时检测、资源受限 |
| `yolov8s.pt` | 22MB | ⚡⚡⚡⚡ | ⭐⭐⭐⭐ | 平衡速度和精度 |
| `yolov8m.pt` | 52MB | ⚡⚡⚡ | ⭐⭐⭐⭐⭐ | 高精度需求 |
| `yolov8l.pt` | 87MB | ⚡⚡ | ⭐⭐⭐⭐⭐ | 离线处理 |
| `yolov8x.pt` | 136MB | ⚡ | ⭐⭐⭐⭐⭐ | 最高精度 |

**推荐**: 使用 `yolov8n.pt` 或 `yolov8s.pt` 以获得最佳性能。

## GPU 加速

如果你有 NVIDIA GPU，可以使用 CUDA 加速：

```bash
# 安装 CUDA 版本的 PyTorch
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118

# 使用 GPU 运行
python demo.py --source video.mp4 --device cuda
```

## 性能优化建议

1. **使用 GPU**: 在 NVIDIA GPU 上运行可提升 5-10 倍速度
2. **选择合适的模型**: `yolov8n` 最快，`yolov8x` 最准确
3. **调整置信度阈值**: 降低阈值可检测更多人脸，但可能增加误检
4. **降低分辨率**: 对于高分辨率视频，可以适当降低分辨率以提高速度

## 与其他模块集成

### DeepFace 特征提取

```python
from face_detection import FaceDetector
from deepface import DeepFace

detector = FaceDetector()

# 检测人脸
faces = detector.detect_image('image.jpg')

# 提取特征
for face in faces:
    face_img = crop_face(image, face['bbox'])
    embedding = DeepFace.represent(face_img, model_name='Facenet')
```

### Milvus 向量存储

```python
from pymilvus import connections, Collection

# 连接 Milvus
connections.connect(host='localhost', port='19530')

# 存储人脸特征向量
collection = Collection('face_embeddings')
collection.insert([embedding])
```

## 常见问题

### Q: 首次运行很慢？
A: YOLOv8 会自动下载模型文件，首次运行需要下载约 6MB 数据。

### Q: 检测不到人脸？
A: 尝试降低置信度阈值：`--conf 0.3`

### Q: 如何提高检测速度？
A: 使用 GPU (`--device cuda`) 或选择更小的模型 (`yolov8n.pt`)

### Q: 支持哪些视频格式？
A: 支持所有 OpenCV 支持的格式：MP4, AVI, MOV, MKV, FLV 等

## 技术支持

如有问题，请查看：
- [YOLOv8 官方文档](https://docs.ultralytics.com/)
- [OpenCV 文档](https://docs.opencv.org/)

## 下一步

- [ ] 集成 DeepFace 进行人脸特征提取
- [ ] 连接 Milvus 进行向量存储和检索
- [ ] 创建 REST API 接口
- [ ] 前端实时展示

---

**版本**: 1.0.0  
**更新日期**: 2026-02-04
