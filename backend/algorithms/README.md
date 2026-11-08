# 抓拍引擎

这个目录负责看视频：YOLOv8 追踪人脸，Facenet512 抽出 512 维向量，再通过 RabbitMQ 交给 Spring Boot。系统怎么串起来，以仓库根目录的 [README](../../README.md) 为准。

## 运行

在仓库根目录激活虚拟环境之后：

```bash
python backend/algorithms/demo.py --source 0 --location "Entrance"
python backend/algorithms/demo.py --source path/to/video.mp4 --location "Main Gate"
```

| 参数 | 作用 | 默认 |
| --- | --- | --- |
| `--source` | 摄像头编号或视频路径 | `0` |
| `--location` | 写入抓拍元数据的地点 | `Unknown` |
| `--conf` | 检测置信度 | `config/settings.json` 里的 `captureConfidence` |
| `--model` | 检测权重 | `models/yolov8n-face.pt` |
| `--no-display` | 不弹预览窗口 | 关闭 |
| `--no-save-faces` | 不把人脸小图写到本地 | 关闭。本地不保存时仍会推送后端 |
| `--output` | 标注视频的输出路径 | 由检测流程生成，供回放上传 |

## Java 调用的脚本

`extract_cli.py` 接收一张图片路径。它先检测并裁出面积最大的脸，再向标准输出打一行 JSON embedding。人物库上传和以脸搜图走这里。调试时可以加 `--no-detect`，改为对整张图提特征。

## 模型

`feature_extraction.py` 把 `DEEPFACE_HOME` 指到 `models/`。找不到 `models/yolov8n-face.pt` 时，检测器会改用通用的 `yolov8n.pt`。
