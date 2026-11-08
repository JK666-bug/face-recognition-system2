# Changelog

版本号沿用开发过程里的 version 5.0。更早的记录按当时的提交整理，只写已经进仓库的行为。

## 5.0.0 — 2026-04-20

第一次打 Release。这一版包含人物库、嫌疑人预警，以及不依赖外部服务的单元测试。

- 人物库：上传建档、标记嫌疑人、合并重复人物、更换代表脸、删除
- 实时预警：命中 `alert_embeddings` 后经 WebSocket 推到 Dashboard，预警会在页面上保留一段时间
- 抓拍入库改为先写 MinIO、再写 Milvus；人物归并、检索、预警使用三套独立阈值
- 单元测试覆盖 JWT、阈值回填、MinIO 元数据解析，`mvn test` 不需要连外部服务
- 文档改成和当前代码一致的架构说明、启动步骤和目录
- 以脸搜图和人物库上传会同时识别 Windows 的 `.venv/Scripts/python.exe` 和 macOS / Linux 的 `.venv/bin/python`
- 依赖里补上抓拍进程实际要 import 的 `pika`
- 不再跟踪 `node_modules`、Maven `target/`、运行日志和编辑器配置

## 4.x — 2026-04-18 至 2026-04-19

- 4.2 调整预警
- 4.1 补齐人物库和预警，并调整库结构
- 4.0 加入预警和人物库，重做抓拍处理，以及 MinIO、Milvus 的划分

## 3.0 — 2026-04-17

- 调整数据库结构

## 2.0 — 2026-04-06

- 收紧检测过程里的比对逻辑

## 1.0 — 2026-03

- 打通 YOLOv8 抓拍、Facenet 特征、RabbitMQ、Spring Boot、Milvus 和 MinIO
- 管理端：总览、轨迹检索、数据集、权限、阈值设置
