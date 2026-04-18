package com.face.recognition.service;

import com.face.recognition.dto.AlertEventDTO;
import com.face.recognition.model.FaceDataMessage;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.milvus.param.R;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Value("${spring.minio.peopleBucketName:people}")
    private String peopleBucketName;

    @Value("${spring.milvus.collectionName}")
    private String faceCollectionName;

    @Value("${spring.milvus.peopleCollectionName:people_embeddings}")
    private String peopleCollectionName;

    @Value("${spring.milvus.alertCollectionName:alert_embeddings}")
    private String alertCollectionName;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private AlertWebSocketService alertWebSocketService;

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

            boolean peopleFound = minioClient
                    .bucketExists(io.minio.BucketExistsArgs.builder().bucket(peopleBucketName).build());
            if (!peopleFound) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(peopleBucketName).build());
                log.info("MinIO People Bucket '{}' created.", peopleBucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO Buckets: ", e);
        }

        // 2. 初始化 Milvus Collection
        initMilvusCollection();
    }

    private void initMilvusCollection() {
        initSingleCollection(faceCollectionName, true);
        initSingleCollection(peopleCollectionName, true);
        initSingleCollection(alertCollectionName, true);
    }

    private void initSingleCollection(String collectionName, boolean includePeopleId) {
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

                List<io.milvus.param.collection.FieldType> schemaFields = new ArrayList<>();
                schemaFields.add(idField);
                if (includePeopleId) {
                    io.milvus.param.collection.FieldType peopleField = io.milvus.param.collection.FieldType.newBuilder()
                            .withName("people_id")
                            .withDataType(io.milvus.grpc.DataType.VarChar)
                            .withMaxLength(64)
                            .build();
                    schemaFields.add(peopleField);
                }
                io.milvus.param.collection.FieldType vectorField = io.milvus.param.collection.FieldType.newBuilder()
                        .withName("vector")
                        .withDataType(io.milvus.grpc.DataType.FloatVector)
                        .withDimension(512)
                        .build();
                schemaFields.add(vectorField);

                // 创建集合
                io.milvus.param.collection.CreateCollectionParam.Builder builder = io.milvus.param.collection.CreateCollectionParam
                        .newBuilder()
                        .withCollectionName(collectionName);
                for (io.milvus.param.collection.FieldType field : schemaFields) {
                    builder.addFieldType(field);
                }
                io.milvus.param.R<?> createRes = milvusClient.createCollection(builder.build());

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
        String faceId = UUID.randomUUID().toString();
        log.info("开始处理 [人脸] 数据，分配 face_id: {}", faceId);

        List<Float> vector = toFloatVector(message.getVector());
        float alertMatchThreshold = settingsService.getAlertMatchThreshold();
        float peopleMatchThreshold = settingsService.getPeopleMatchThreshold();
        MatchResult alertMatch = searchPeople(alertCollectionName, vector, 1, alertMatchThreshold);
        MatchResult peopleMatch = searchPeople(peopleCollectionName, vector, 1, peopleMatchThreshold);

        String peopleId = peopleMatch.getPeopleId();
        boolean isNewPerson = false;
        if (peopleId == null || peopleId.isEmpty()) {
            peopleId = UUID.randomUUID().toString();
            isNewPerson = true;
        }

        // 1. 保存到对象数据库 (使用 MinIO)
        saveToObjectDatabase(faceId, peopleId, message, alertMatch);

        if (alertMatch.isHit()) {
            alertWebSocketService.broadcast(AlertEventDTO.builder()
                    .type("SUSPECT_ALERT")
                    .faceId(faceId)
                    .peopleId(peopleId)
                    .alertPeopleId(alertMatch.getPeopleId())
                    .location(message.getLocation())
                    .timestamp(safeTimestamp(message.getTimestamp()))
                    .imageBase64(message.getImage_base64())
                    .build());
        }

        // 2. 保存到向量数据库 (使用 Milvus)
        saveToVectorDatabase(faceCollectionName, faceId, peopleId, vector);

        // 3. 维护人物底库
        upsertPeopleObject(peopleId, faceId, message, isNewPerson);
        if (isNewPerson) {
            saveToVectorDatabase(peopleCollectionName, faceId, peopleId, vector);
        }
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

    private void saveToObjectDatabase(String faceId, String peopleId, FaceDataMessage message, MatchResult alertMatch) {
        try {
            // Base64 解码回图片字节流
            byte[] imageBytes = Base64.getDecoder().decode(message.getImage_base64());
            String fileName = faceId + ".jpg";

            // 构造元数据 (封装拍摄信息)
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("location", message.getLocation());
            metadata.put("timestamp", message.getTimestamp());
            metadata.put("people_id", peopleId);
            metadata.put("alert_hit", String.valueOf(alertMatch.isHit()));
            if (message.getQuality_score() != null) {
                metadata.put("quality_score", String.valueOf(message.getQuality_score()));
            }
            if (message.getCapture_reason() != null) {
                metadata.put("capture_reason", message.getCapture_reason());
            }
            if (alertMatch.getPeopleId() != null) {
                metadata.put("alert_people_id", alertMatch.getPeopleId());
            }
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

            log.info("Image and metadata successfully saved to MinIO [face_id: {}, people_id: {}, Location: {}]", faceId,
                    peopleId,
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

    private void saveToVectorDatabase(String collectionName, String faceId, String peopleId, List<Float> floatVector) {
        try {
            java.util.List<String> ids = java.util.Collections.singletonList(faceId);
            java.util.List<String> peopleIds = java.util.Collections.singletonList(peopleId);
            java.util.List<java.util.List<Float>> vectors = java.util.Collections.singletonList(floatVector);

            java.util.List<io.milvus.param.dml.InsertParam.Field> fields = new java.util.ArrayList<>();
            fields.add(new io.milvus.param.dml.InsertParam.Field("face_id", ids));
            fields.add(new io.milvus.param.dml.InsertParam.Field("people_id", peopleIds));
            fields.add(new io.milvus.param.dml.InsertParam.Field("vector", vectors));

            io.milvus.param.R<?> response = milvusClient.insert(
                    io.milvus.param.dml.InsertParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFields(fields)
                            .build());

            if (response.getStatus() != io.milvus.param.R.Status.Success.getCode()) {
                if (faceCollectionName.equals(collectionName) && response.getMessage() != null
                        && response.getMessage().toLowerCase().contains("people_id")) {
                    // 兼容已有老 face_embeddings 表结构（无 people_id 字段）
                    log.warn("face collection has no people_id field, fallback insert without people_id");
                    saveToLegacyFaceCollection(faceId, floatVector);
                    return;
                }
                log.error("Failed to insert vector into Milvus: {}", response.getMessage());
            } else {
                log.info("Feature vector successfully saved to Milvus [collection: {}, face_id: {}, people_id: {}, Dimension: {}]",
                        collectionName,
                        faceId,
                        peopleId,
                        floatVector.size());
            }
        } catch (Exception e) {
            log.error("Exception during Milvus insertion: ", e);
        }
    }

    private void saveToLegacyFaceCollection(String faceId, List<Float> floatVector) {
        List<io.milvus.param.dml.InsertParam.Field> legacyFields = new ArrayList<>();
        legacyFields.add(new io.milvus.param.dml.InsertParam.Field("face_id", Collections.singletonList(faceId)));
        legacyFields.add(new io.milvus.param.dml.InsertParam.Field("vector", Collections.singletonList(floatVector)));
        milvusClient.insert(io.milvus.param.dml.InsertParam.newBuilder()
                .withCollectionName(faceCollectionName)
                .withFields(legacyFields)
                .build());
    }

    private List<Float> toFloatVector(List<Double> vector) {
        if (vector == null) {
            return Collections.emptyList();
        }
        return vector.stream().map(Double::floatValue).collect(Collectors.toList());
    }

    private void upsertPeopleObject(String peopleId, String faceId, FaceDataMessage message, boolean isNewPerson) {
        String objectName = peopleId + ".jpg";
        try {
            byte[] imageBytes = Base64.getDecoder().decode(message.getImage_base64());
            Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("people_id", peopleId);
            metadata.put("face_id", faceId);
            metadata.put("last_time", safeTimestamp(message.getTimestamp()));
            metadata.put("is_suspect", readSuspectFlag(peopleId));
            String incomingVideoId = message.getVideo_id();
            double incomingScore = message.getQuality_score() != null ? message.getQuality_score() : 0.0;
            metadata.put("representative_video_id", incomingVideoId == null ? "" : incomingVideoId);
            metadata.put("representative_score", String.valueOf(incomingScore));

            if (isNewPerson) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(peopleBucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                        .contentType("image/jpeg")
                        .userMetadata(metadata)
                        .build());
            } else {
                byte[] currentImage = imageBytes;
                Map<String, String> currentMeta = new java.util.HashMap<>();
                try (java.io.InputStream stream = minioClient.getObject(io.minio.GetObjectArgs.builder()
                        .bucket(peopleBucketName)
                        .object(objectName)
                        .build())) {
                    currentImage = stream.readAllBytes();
                } catch (Exception ignored) {
                }
                try {
                    io.minio.StatObjectResponse stat = minioClient.statObject(io.minio.StatObjectArgs.builder()
                            .bucket(peopleBucketName)
                            .object(objectName)
                            .build());
                    currentMeta.putAll(stat.userMetadata());
                } catch (Exception ignored) {
                }

                // 同一视频内只保留最高分代表图；跨视频仅在更高分时替换
                String currentVideoId = currentMeta.getOrDefault("representative_video_id", "");
                double currentScore = parseDoubleOrDefault(currentMeta.get("representative_score"), 0.0);
                boolean shouldReplace = incomingScore > currentScore;
                if (!shouldReplace) {
                    metadata.put("face_id", currentMeta.getOrDefault("face_id", faceId));
                    metadata.put("representative_video_id", currentVideoId);
                    metadata.put("representative_score", String.valueOf(currentScore));
                }

                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(peopleBucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(currentImage), currentImage.length, -1)
                        .contentType("image/jpeg")
                        .userMetadata(metadata)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to upsert people object for people_id {}: {}", peopleId, e.getMessage());
        }
    }

    private String readSuspectFlag(String peopleId) {
        try {
            io.minio.StatObjectResponse stat = minioClient.statObject(io.minio.StatObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .build());
            return stat.userMetadata().getOrDefault("is_suspect", "false");
        } catch (Exception e) {
            return "false";
        }
    }

    private String safeTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return ts;
    }

    private double parseDoubleOrDefault(String val, double dft) {
        if (val == null || val.isBlank()) {
            return dft;
        }
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    private MatchResult searchPeople(String collectionName, List<Float> embedding, int topK, float threshold) {
        if (embedding == null || embedding.isEmpty()) {
            return MatchResult.empty();
        }
        try {
            io.milvus.param.dml.SearchParam searchParam = io.milvus.param.dml.SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withOutFields(List.of("face_id", "people_id"))
                    .withTopK(topK)
                    .withFloatVectors(List.of(embedding))
                    .withVectorFieldName("vector")
                    .withParams("{\"nprobe\":10}")
                    .build();
            R<io.milvus.grpc.SearchResults> response = milvusClient.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                return MatchResult.empty();
            }
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    response.getData().getResults());
            List<io.milvus.response.SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
            if (idScores.isEmpty() || idScores.get(0).getScore() < threshold) {
                return MatchResult.empty();
            }
            io.milvus.response.SearchResultsWrapper.IDScore hit = idScores.get(0);
            Object peopleObj = wrapper.getFieldData("people_id", 0).get(0);
            String peopleId = peopleObj == null ? null : String.valueOf(peopleObj);
            return new MatchResult(true, peopleId, hit.getStrID(), hit.getScore());
        } catch (Exception e) {
            log.warn("Search in {} failed: {}", collectionName, e.getMessage());
            return MatchResult.empty();
        }
    }

    private static class MatchResult {
        private final boolean hit;
        private final String peopleId;
        private final String faceId;
        private final float score;

        private MatchResult(boolean hit, String peopleId, String faceId, float score) {
            this.hit = hit;
            this.peopleId = peopleId;
            this.faceId = faceId;
            this.score = score;
        }

        static MatchResult empty() {
            return new MatchResult(false, null, null, 0f);
        }

        public boolean isHit() {
            return hit;
        }

        public String getPeopleId() {
            return peopleId;
        }

        @SuppressWarnings("unused")
        public String getFaceId() {
            return faceId;
        }

        @SuppressWarnings("unused")
        public float getScore() {
            return score;
        }
    }
}
