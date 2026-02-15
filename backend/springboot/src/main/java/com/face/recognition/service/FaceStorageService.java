package com.face.recognition.service;

import com.face.recognition.model.FaceDataMessage;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 1. 初始化 MinIO Bucket
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("📁 MinIO Bucket '{}' 不存在，已自动创建。", bucketName);
            }
        } catch (Exception e) {
            log.error("❌ 初始化 MinIO Bucket 失败: ", e);
        }

        // 2. 初始化 Milvus Collection
        initMilvusCollection();
    }

    private void initMilvusCollection() {
        try {
            io.milvus.param.R<Boolean> response = milvusClient.hasCollection(
                    io.milvus.param.collection.HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (response.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                log.error("❌ 检查 Milvus 集合失败: {}", response.getMessage());
                return;
            }

            boolean exists = response.getData();

            if (!exists) {
                log.info("🧬 Milvus 集合 '{}' 不存在，正在创建...", collectionName);

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
                milvusClient.createCollection(
                        io.milvus.param.collection.CreateCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .addFieldType(idField)
                                .addFieldType(vectorField)
                                .build());

                // 创建索引 (加速搜索)
                milvusClient.createIndex(
                        io.milvus.param.index.CreateIndexParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withFieldName("vector")
                                .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                                .withMetricType(io.milvus.param.MetricType.L2)
                                .withExtraParam("{\"nlist\":1024}")
                                .build());

                // 加载集合到内存
                milvusClient.loadCollection(
                        io.milvus.param.collection.LoadCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build());
                log.info("✅ Milvus 集合 '{}' 初始化并加载完成。", collectionName);
            }
        } catch (Exception e) {
            log.error("❌ 初始化 Milvus 失败: ", e);
        }
    }

    /**
     * 执行双库保存逻辑
     * 
     * @param message 从 MQ 接收到的原始数据
     */
    public void saveFaceData(FaceDataMessage message) {
        String uuid = UUID.randomUUID().toString();
        log.info("开始处理人脸数据，分配 ID: {}", uuid);

        // 1. 保存到对象数据库 (使用 MinIO)
        saveToObjectDatabase(uuid, message);

        // 2. 保存到向量数据库 (使用 Milvus)
        saveToVectorDatabase(uuid, message);
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

            // 存入 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType("image/jpeg")
                            .userMetadata(metadata) // 重点：将数据作为元数据存入
                            .build());

            log.info("🏠 图片与元数据已成功存入 MinIO [ID: {}, 位置: {}]", uuid, message.getLocation());
        } catch (Exception e) {
            log.error("❌ 存入 MinIO 失败: ", e);
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

            milvusClient.insert(
                    io.milvus.param.dml.InsertParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFields(fields)
                            .build());

            log.info("🧬 特征向量已成功存入 Milvus [ID: {}, 维度: {}]", uuid, message.getVector().size());
        } catch (Exception e) {
            log.error("❌ 存入 Milvus 失败: ", e);
        }
    }
}
