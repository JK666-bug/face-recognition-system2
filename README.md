# face-recognition-system
face-recognition-system
# 项目目录结构

```
Face-Recognition-System/
│
├── backend/                          # 后端服务文件
│   ├── algorithms/                   # 面部识别算法 (机器学习部分)
│   │   ├── face_detection.py         # 面部检测 (使用 YOLOv8)
│   │   ├── feature_extraction.py     # 特征提取 (使用 DeepFace)
│   │   ├── vector_storage.py         # 向量存储与查询 (使用 Milvus)
│   │   └── utils.py                  # 辅助函数 (如图像预处理)
│   │
│   ├── springboot_backend/           # Spring Boot 后端服务 (业务逻辑部分)
│   │   ├── controllers/              # 控制器层，处理 API 请求
│   │   │   ├── face_recognition_controller.py # 面部识别相关 API
│   │   │   ├── image_controller.py   # 图像上传与存储 API
│   │   │   └── user_controller.py    # 用户认证 API
│   │   ├── services/                 # 业务逻辑层
│   │   │   ├── face_recognition_service.py  # 业务逻辑服务，调用算法部分
│   │   │   ├── image_service.py      # 图像上传与存储服务 (与 MinIO 交互)
│   │   │   └── user_service.py       # 用户认证与权限管理服务
│   │   ├── models/                   # 数据模型
│   │   │   ├── user.py               # 用户模型
│   │   │   ├── activity.py           # 活动跟踪模型
│   │   │   └── face_data.py          # 面部特征数据模型
│   │   ├── config/                   # 配置文件
│   │   │   ├── milvus_config.py      # Milvus 配置
│   │   │   ├── minio_config.py       # MinIO 配置
│   │   │   └── config.py             # 其他配置文件
│   │   ├── app.py                    # Spring Boot 后端主文件
│   │   └── requirements.txt          # 后端依赖库
│   │
│   ├── database/                     # 数据库相关文件
│   │   ├── migrations/               # 数据库迁移脚本
│   │   └── db_init.py                # 初始化数据库脚本
│   │
│   ├── Dockerfile                    # Docker 配置文件（容器化部署）
│   └── README.md                     # 后端相关说明
│
├── frontend/                         # 前端文件
│   ├── public/                       # 静态文件
│   ├── src/                          
│   │   ├── components/               # React 组件
│   │   ├── pages/                    # 页面组件
│   │   ├── services/                 # 与后端交互的服务
│   │   ├── App.js                    # 主 React 组件
│   │   └── styles/                   # 样式文件
│   ├── package.json                  # 前端依赖和配置
│   └── webpack.config.js             # Webpack 配置
│
├── database/                         # 数据库存储和数据处理
│   ├── milvus/                       # Milvus 相关配置和数据
│   ├── minio/                        # MinIO 图像存储
│   └── db_backup/                    # 数据库备份
│
├── docs/                             # 项目文档
│   ├── system_design.md              # 系统设计文档
│   ├── api_documentation.md          # API 文档
│   └── user_manual.md                # 用户手册
│
├── scripts/                          # 脚本
│   ├── deploy.sh                     # 部署脚本
│   ├── setup_database.sh             # 数据库设置脚本
│   ├── test.sh                       # 测试脚本
│   └── run_server.sh                 # 启动后端服务器的脚本
│
├── .gitignore                        # Git 忽略文件
├── README.md                         # 项目概述和设置说明
└── LICENSE                           # 许可证文件


```
