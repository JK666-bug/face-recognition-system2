package com.face.recognition.service;

import com.face.recognition.model.FaceDataMessage;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.milvus.param.R;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class FaceStorageService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private io.milvus.client.MilvusServiceClient milvusClient;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    @Value("${spring.minio.placesBucketName}")
    private String placesBucketName;

    @Value("${spring.minio.videoBucketName:videos}")
    private String videoBucketName;

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 1. 初始化 MinIO Bucket
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO Bucket '{}' created.", bucketName);
            }

            boolean placesFound = minioClient
                    .bucketExists(io.minio.BucketExistsArgs.builder().bucket(placesBucketName).build());
            if (!placesFound) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(placesBucketName).build());
                log.info("MinIO Places Bucket '{}' created.", placesBucketName);
            }

            boolean videoFound = minioClient
                    .bucketExists(io.minio.BucketExistsArgs.builder().bucket(videoBucketName).build());
            if (!videoFound) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(videoBucketName).build());
                log.info("MinIO Video Bucket '{}' created.", videoBucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO Buckets: ", e);
        }

        // 2. 初始化 Milvus Collection
        initMilvusCollection();
    }

    private void initMilvusCollection() {
        try {
            log.info("Checking Milvus collection: {}", collectionName);
            io.milvus.param.R<Boolean> response = milvusClient.hasCollection(
                    io.milvus.param.collection.HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (response.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                log.error("Failed to check Milvus collection status: {}, Message: {}",
                        response.getStatus(), response.getMessage());
                return;
            }

            boolean exists = response.getData();

            if (!exists) {
                log.info("Milvus collection '{}' does not exist, creating...", collectionName);

                // 定义字段
                io.milvus.param.collection.FieldType idField = io.milvus.param.collection.FieldType.newBuilder()
                        .withName("face_id")
                        .withDataType(io.milvus.grpc.DataType.VarChar)
                        .withMaxLength(64)
                        .withPrimaryKey(true)
                        .build();

                io.milvus.param.collection.FieldType vectorField = io.milvus.param.collection.FieldType.newBuilder()
                        .withName("vector")
                        .withDataType(io.milvus.grpc.DataType.FloatVector)
                        .withDimension(512)
                        .build();

                // 创建集合
                io.milvus.param.R<?> createRes = milvusClient.createCollection(
                        io.milvus.param.collection.CreateCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .addFieldType(idField)
                                .addFieldType(vectorField)
                                .build());

                if (createRes.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                    log.error("Failed to create Milvus collection: {}", createRes.getMessage());
                    return;
                }

                // 创建索引 (加速搜索)
                io.milvus.param.R<?> indexRes = milvusClient.createIndex(
                        io.milvus.param.index.CreateIndexParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withFieldName("vector")
                                .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                                .withMetricType(io.milvus.param.MetricType.COSINE)
                                .withExtraParam("{\"nlist\":1024}")
                                .build());

                if (indexRes.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                    log.error("Failed to create Milvus index: {}", indexRes.getMessage());
                }

                // 加载集合到内存
                io.milvus.param.R<?> loadRes = milvusClient.loadCollection(
                        io.milvus.param.collection.LoadCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build());

                if (loadRes.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                    log.error("Failed to load Milvus collection: {}", loadRes.getMessage());
                } else {
                    log.info("Milvus collection '{}' initialized and loaded.", collectionName);
                }
            } else {
                log.info("Milvus collection '{}' already exists.", collectionName);
                // 确保已加载
                milvusClient.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName).build());
            }
        } catch (Exception e) {
            log.error("Exception during Milvus initialization: ", e);
        }
    }

    /**
     * 执行双库保存逻辑
     * 
     * @param message 从 MQ 接收到的原始数据
     */
    public void saveFaceData(FaceDataMessage message) {
        // 根据消息类型分发处理
        if ("VIDEO".equalsIgnoreCase(message.getType())) {
            handleVideoData(message);
        } else {
            // 默认为 FACE 类型或旧版本兼容
            handleFaceData(message);
        }
    }

    private void handleFaceData(FaceDataMessage message) {
        String uuid = UUID.randomUUID().toString();
        log.info("开始处理 [人脸] 数据，分配 ID: {}", uuid);

        // 1. 保存到对象数据库 (使用 MinIO)
        saveToObjectDatabase(uuid, message);

        // 2. 保存到向量数据库 (使用 Milvus)
        saveToVectorDatabase(uuid, message);
    }

    private void handleVideoData(FaceDataMessage message) {
        if (message.getVideo_id() == null || message.getVideo_base64() == null) {
            log.warn("Received invalid video message: video_id or video_base64 is null");
            return;
        }

        try {
            log.info("Processing [VIDEO] data via MQ: {}", message.getVideo_id());
            // Base64 解码视频文件
            byte[] videoBytes = Base64.getDecoder().decode(message.getVideo_base64());

            // 存入 MinIO 的视频桶
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(videoBucketName)
                            .object(message.getVideo_id())
                            .stream(new ByteArrayInputStream(videoBytes), videoBytes.length, -1)
                            .contentType("video/mp4")
                            .build());

            log.info("Video transmitted from MQ and saved to MinIO: {}", message.getVideo_id());
        } catch (Exception e) {
            log.error("Failed to save video to MinIO: ", e);
        }
    }

    private void saveToObjectDatabase(String uuid, FaceDataMessage message) {
        try {
            // Base64 解码回图片字节流
            byte[] imageBytes = Base64.getDecoder().decode(message.getImage_base64());
            String fileName = uuid + ".jpg";

            // 构造元数据 (封装拍摄信息)
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("location", message.getLocation());
            metadata.put("timestamp", message.getTimestamp());
            if (message.getVideo_id() != null) {
                metadata.put("video_id", message.getVideo_id());
            }

            // 存入 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType("image/jpeg")
                            .userMetadata(metadata) // 重点：将数据作为元数据存入
                            .build());

            log.info("Image and metadata successfully saved to MinIO [ID: {}, Location: {}]", uuid,
                    message.getLocation());
        } catch (Exception e) {
            log.error("Failed to save to MinIO: ", e);
        }

        // 1.1 注册位置到 places bucket
        registerLocation(message.getLocation());
    }

    private void registerLocation(String location) {
        if (location == null || location.isEmpty())
            return;
        try {
            // 检查位置标记是否存在 (以地名为对象名，0字节内容)
            try {
                minioClient.statObject(
                        io.minio.StatObjectArgs.builder()
                                .bucket(placesBucketName)
                                .object(location)
                                .build());
            } catch (io.minio.errors.ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    // 不存在则创建
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(placesBucketName)
                                    .object(location)
                                    .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                                    .build());
                    log.info("Registered new location: {}", location);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to register location {}: {}", location, e.getMessage());
        }
    }

    private void saveToVectorDatabase(String uuid, FaceDataMessage message) {
        try {
            java.util.List<String> ids = java.util.Collections.singletonList(uuid);

            // 将 List<Double> 转换为 List<Float> (Milvus 要求)
            java.util.List<Float> floatVector = message.getVector().stream()
                    .map(Double::floatValue)
                    .collect(java.util.stream.Collectors.toList());
            java.util.List<java.util.List<Float>> vectors = java.util.Collections.singletonList(floatVector);

            java.util.List<io.milvus.param.dml.InsertParam.Field> fields = new java.util.ArrayList<>();
            fields.add(new io.milvus.param.dml.InsertParam.Field("face_id", ids));
            fields.add(new io.milvus.param.dml.InsertParam.Field("vector", vectors));

            io.milvus.param.R<?> response = milvusClient.insert(
                    io.milvus.param.dml.InsertParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFields(fields)
                            .build());

            if (response.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                log.error("Failed to insert vector into Milvus: {}", response.getMessage());
            } else {
                log.info("Feature vector successfully saved to Milvus [ID: {}, Dimension: {}]", uuid,
                        message.getVector().size());
            }
        } catch (Exception e) {
            log.error("Exception during Milvus insertion: ", e);
        }
    }
}
