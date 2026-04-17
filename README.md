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

## 🚀 在一台全新电脑上运行本项目的完整指南

如果本工程需要在另一台全新电脑上部署并运行，请严格按照以下 **从底层到上层** 的顺序依次配置和启动。

### 准备工作 (全新电脑必备环境)
1. **安装 Docker Desktop** (并确保运行正常，开启 WSL2 支持)
2. **安装 Git 或下载项目源码**
3. **安装 Java JDK 17 及以上** 
4. **安装 Node.js (推荐 v18+)** 
5. **安装 Python 3.10 及以上** 

---

### 第一步：启动基础中间件与数据库 (Docker)
项目依赖的数据库、消息队列和存储服务完全容器化，只需一键启动。

1. 打开一个终端，进入项目根目录：
   ```bash
   cd face-recognition-system
   ```
2. 使用 Docker Compose 构建并启动所有的后台服务组件（如 MySQL, Milvus, MinIO, RabbitMQ等）：
   *(如果这是新电脑首次运行，Docker 会从网上拉取镜像，这个过程可能需要几十分钟，取决于网速。)*
   ```bash
   docker-compose up -d
   ```
   > **验证服务状态**：
   > - RabbitMQ 管理后台：`http://localhost:15672` (guest / guest)
   > - MinIO 对象存储：`http://localhost:30001` (minioadmin / minioadmin)
注:milvus数据库的collection名称配置已经在application，剩下字段要重新手动配置
ID,vector,嫌疑人name
---

### 第二步：启动前端 Web 界面
前端使用 React + Vite 构建。在全新电脑上，由于尚未下载 `node_modules`，必须先安装前端依赖库。

1. 保持上一个终端不动，再开启一个**新**的终端。
2. 进入前端目录：
   ```bash
   cd frontend
   ```
3. **[新电脑首次运行必做]** 安装所有前端依赖模块：
   ```bash
   npm install
   ```
   *(等进度条跑完即可，以后只要依赖没变就不用再敲这句了。)*
4. 启动前端开发服务器：
   ```bash
   npm run dev
   ```
   *控制台会打印出如 `http://localhost:5173` 的访问地址，先不要管，等后端启动后再访问。*

---

### 第三步：启动 Java Spring Boot 后端
后端负责协调存储、数据库、消息队列并向前端提供 API。在新电脑上，我们可以利用 IDE 自动帮我们解决依赖问题，不需要手动操作额外的命令。

1. 打开 **VS Code** (确保安装了 Extension Pack for Java 插件) 或 **IntelliJ IDEA**。
2. 用 IDE 打开 `backend/springboot` 文件夹或者整个 `face-recognition-system` 项目根目录。
3. 等待几分钟，IDE 会**自动下载 (Sync)** 项目中 `pom.xml` 所声明的所有 Java 依赖包（如 Spring 核心包等）。
4. 依赖下载完毕后，找到主程序入口文件：
   `backend/springboot/src/main/java/com/face/recognition/FaceRecognitionApplication.java`
5. 点击文件中的 **Run (运行)** 按钮启动应用。
6. 等控制台最终输出 `Started FaceRecognitionApplication in X seconds` 说明后端部署并在 `8080` 端口监听成功。

*(现在可以去浏览器打开第二步的 `http://localhost:5173` 访问并登录系统了！)*

---

### 第四步：配置并启动 Python 算法引擎（人脸抓拍）
负责实际在监控视频中抠图并抽取特征的最核心算法部分在这个环节拉起。由于新电脑不自带库，必须重新创建沙盒环境并下载所有 AI 算法依赖。

1. 再打开一个**全新**的终端，保持在项目的根目录下：`face-recognition-system`。
2. **[新电脑首次运行]** 创建一份全新的 Python 虚拟沙盒环境：
   ```bash
   python -m venv .venv
   ```
3. 激活这个刚创建的虚拟环境：
   * **Windows**: `.\.venv\Scripts\Activate.ps1`
   * *(如果报错提示脚本被禁用，可以先用管理员权限打开 PowerShell，输入 `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`)*
4. **[新电脑首次运行必做]** 安装算法计算依赖图库（非常大，例如 TensorFlow, OpenCV, PyTorch）：
   ```bash
   pip install -r backend/algorithms/requirements.txt
   ```
5. 安装完成后，就可以正式启动并指定数据源了（注意：运行该指令前务必保证终端前面有绿色的 `(.venv)` 前缀）：
   * 👉 **处理某段监控录像**：
     ```bash
     python backend\algorithms\demo.py --source backend\algorithms\media\face3.mp4 --location "Main Gate"
     ```
   * 👉 **或直接开启笔记本电脑摄像头实时直播抓拍**：
     ```bash
     python backend\algorithms\demo.py --source 0 --location "Entrance"
     ```

---

只要你在算法终端看到了 “*🚀 YOLOv8 实时面部追踪与自动去重系统*”，就说明整条链路已经彻底打通打通，你跑着算法，可以直接去前端查看动态流进来的抓拍信息了！

```
1. 创建虚拟环境
.\.venv\Scripts\Activate.ps1

2.docker
停止并删除容器，同时删除关联的数据卷 (-v 表示 volumes)
docker-compose down -v

docker-compose up -d 重建
docker-compose start 重启
docker-compose stop

注:若docker启动有问题，查看自己电脑是否安装过mysql80服务，要禁止服务，使得mysql服务器启动在docker而不是本地电脑

3.开启终端
backend\springboot\src\main\java\com\face\recognition\FaceRecognitionApplication.java

4. 开启识别算法
python backend\algorithms\demo.py --source backend\algorithms\media\face3.mp4 --location home
python backend\algorithms\demo.py --location meetingroom

5. 前端
(.venv) PS D:\前后端开发\前端\vs code 代码\face-recognition-system\frontend> npm run dev
```


1. RabbitMQ 账号密码
用户名：
密　码：guest
管理后台地址：http://localhost:15672


2. MinIO 账号密码
用户名 (Access Key)：minioadmin
密　码 (Secret Key)：minioadmin
控制台地址：http://localhost:30001

3. Milvus 登录/连接信息
用户名：留空（不需要输入）
密　码：留空（不需要输入）
Milvus 地址：localhost:19530

4. 查看端口状态：netstat -ano | findstr :8080
5. 杀掉8080端口的进程：Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess -Force