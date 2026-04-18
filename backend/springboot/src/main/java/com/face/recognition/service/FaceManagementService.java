package com.face.recognition.service;

import com.face.recognition.dto.CaptureRecordDTO;
import com.face.recognition.dto.PeopleRecordDTO;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FaceManagementService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private FaceSearchService faceSearchService;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    @Value("${spring.minio.videoBucketName:videos}")
    private String videoBucketName;

    @Value("${spring.minio.peopleBucketName:people}")
    private String peopleBucketName;

    @Value("${spring.milvus.peopleCollectionName:people_embeddings}")
    private String peopleCollectionName;

    @Value("${spring.milvus.alertCollectionName:alert_embeddings}")
    private String alertCollectionName;

    public List<CaptureRecordDTO> listAllCaptures() {
        List<CaptureRecordDTO> records = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) continue;

                String objectName = item.objectName();
                String id = objectName.replace(".jpg", "");

                try {
                    StatObjectResponse stat = minioClient.statObject(
                            StatObjectArgs.builder().bucket(bucketName).object(objectName).build());

                    Map<String, String> meta = stat.userMetadata();
                    String location = meta.getOrDefault("location", 
                                      meta.getOrDefault("location".toLowerCase(), 
                                      meta.getOrDefault("x-amz-meta-location", "Unknown")));
                    
                    String timestamp = meta.getOrDefault("timestamp", 
                                       meta.getOrDefault("timestamp".toLowerCase(), 
                                       meta.getOrDefault("x-amz-meta-timestamp", item.lastModified().toString())));

                    // 为列表视图获取小图缩略图
                    String base64 = "";
                    try (InputStream stream = minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
                        byte[] bytes = stream.readAllBytes();
                        base64 = Base64.getEncoder().encodeToString(bytes);
                    }

                    // 获取关联视频 ID
                    String videoId = meta.get("video_id");
                    if (videoId == null) videoId = meta.get("video-id");
                    if (videoId == null) videoId = meta.get("x-amz-meta-video_id");
                    if (videoId == null) videoId = meta.get("x-amz-meta-video-id");

                    String videoUrl = null;
                    if (videoId != null && !videoId.isEmpty()) {
                        try {
                            videoUrl = minioClient.getPresignedObjectUrl(
                                    GetPresignedObjectUrlArgs.builder()
                                            .method(io.minio.http.Method.GET)
                                            .bucket(videoBucketName)
                                            .object(videoId)
                                            .expiry(2, java.util.concurrent.TimeUnit.HOURS)
                                            .build());
                        } catch (Exception ve) {
                            log.warn("Failed to generate presigned URL for video {}: {}", videoId, ve.getMessage());
                        }
                    }

                    records.add(CaptureRecordDTO.builder()
                            .id(id)
                            .peopleId(meta.get("people_id"))
                            .imageBase64(base64)
                            .location(location)
                            .timestamp(timestamp)
                            .videoId(videoId)
                            .videoUrl(videoUrl)
                            .alertHit(Boolean.parseBoolean(meta.getOrDefault("alert_hit", "false")))
                            .alertPeopleId(meta.get("alert_people_id"))
                            .build());

                } catch (Exception e) {
                    log.error("Failed to load details for object: {}", objectName);
                }
            }
            
            // 排序：时间倒序
            records.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        } catch (Exception e) {
            log.error("Failed to list captures from MinIO", e);
        }
        return records;
    }

    public void deleteCapture(String id) {
        String objectName = id + ".jpg";
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
            milvusClient.delete(
                    DeleteParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withExpr("face_id == \"" + id + "\"")
                            .build());
            log.info("Successfully deleted capture and vector for ID: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete capture {}: {}", id, e.getMessage());
        }
    }

    public void deleteAllCaptures() {
        log.warn("Request to clear ALL capture records.");
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            
            for (Result<Item> result : results) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucketName).object(result.get().objectName()).build());
            }

            milvusClient.delete(
                    DeleteParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withExpr("face_id != \"\"")
                            .build());
            log.info("All images and vectors cleared.");
        } catch (Exception e) {
            log.error("Failed to clear all captures: {}", e.getMessage());
        }
    }

    public InputStream getVideo(String videoId) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(videoBucketName)
                        .object(videoId)
                        .build());
    }

    public List<PeopleRecordDTO> listPeopleLibrary() {
        List<PeopleRecordDTO> records = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(peopleBucketName).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) {
                    continue;
                }
                String objectName = item.objectName();
                if (!objectName.endsWith(".jpg")) {
                    continue;
                }
                String peopleId = objectName.replace(".jpg", "");
                try {
                    StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                            .bucket(peopleBucketName)
                            .object(objectName)
                            .build());
                    Map<String, String> meta = stat.userMetadata();
                    String imageBase64 = "";
                    try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(peopleBucketName)
                            .object(objectName)
                            .build())) {
                        imageBase64 = Base64.getEncoder().encodeToString(stream.readAllBytes());
                    }

                    records.add(PeopleRecordDTO.builder()
                            .peopleId(peopleId)
                            .representativeFaceId(meta.get("face_id"))
                            .imageBase64(imageBase64)
                            .lastTime(meta.getOrDefault("last_time", item.lastModified().toString()))
                            .suspect(Boolean.parseBoolean(meta.getOrDefault("is_suspect", "false")))
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to load people object {}: {}", objectName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to list people library: {}", e.getMessage());
        }
        records.sort((a, b) -> b.getLastTime().compareTo(a.getLastTime()));
        return records;
    }

    public PeopleRecordDTO createPersonFromUpload(byte[] imageBytes, boolean suspect) throws Exception {
        List<Float> embedding = faceSearchService.extractFeatureFromBytes(imageBytes, ".jpg");
        String peopleId = UUID.randomUUID().toString();
        String faceId = UUID.randomUUID().toString();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        java.util.Map<String, String> faceMetadata = new java.util.HashMap<>();
        faceMetadata.put("location", "PEOPLE_LIBRARY_UPLOAD");
        faceMetadata.put("timestamp", now);
        faceMetadata.put("people_id", peopleId);
        faceMetadata.put("alert_hit", "false");
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(faceId + ".jpg")
                .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                .contentType("image/jpeg")
                .userMetadata(faceMetadata)
                .build());

        java.util.Map<String, String> peopleMetadata = new java.util.HashMap<>();
        peopleMetadata.put("people_id", peopleId);
        peopleMetadata.put("face_id", faceId);
        peopleMetadata.put("last_time", now);
        peopleMetadata.put("is_suspect", String.valueOf(suspect));
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(peopleBucketName)
                .object(peopleId + ".jpg")
                .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                .contentType("image/jpeg")
                .userMetadata(peopleMetadata)
                .build());

        insertEmbedding(collectionName, faceId, peopleId, embedding);
        insertEmbedding(peopleCollectionName, faceId, peopleId, embedding);
        if (suspect) {
            insertEmbedding(alertCollectionName, faceId, peopleId, embedding);
        }

        return PeopleRecordDTO.builder()
                .peopleId(peopleId)
                .representativeFaceId(faceId)
                .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                .lastTime(now)
                .suspect(suspect)
                .build();
    }

    public void setPeopleSuspect(String peopleId, boolean suspect) {
        String objectName = peopleId + ".jpg";
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(objectName)
                    .build());
            Map<String, String> meta = new java.util.HashMap<>(stat.userMetadata());
            meta.put("is_suspect", String.valueOf(suspect));

            byte[] imageBytes;
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(objectName)
                    .build())) {
                imageBytes = stream.readAllBytes();
            }
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(objectName)
                    .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType("image/jpeg")
                    .userMetadata(meta)
                    .build());

            // 先删后插，保持嫌疑人库和标记一致
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(alertCollectionName)
                    .withExpr("people_id == \"" + peopleId + "\"")
                    .build());

            if (suspect) {
                String faceId = meta.get("face_id");
                if (faceId != null && !faceId.isBlank()) {
                    List<Float> embedding = faceSearchService.extractFeatureFromBytes(imageBytes, ".jpg");
                    insertEmbedding(alertCollectionName, faceId, peopleId, embedding);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update suspect flag for people_id " + peopleId, e);
        }
    }

    public void deletePerson(String peopleId) {
        String peopleObjectName = peopleId + ".jpg";
        try {
            // 删除 people 桶对象
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleObjectName)
                    .build());

            // 删除抓拍库中该人物所有图片
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                    continue;
                }
                try {
                    StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(item.objectName())
                            .build());
                    String pid = stat.userMetadata().get("people_id");
                    if (peopleId.equals(pid)) {
                        String faceId = item.objectName().replace(".jpg", "");
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(item.objectName())
                                .build());
                        milvusClient.delete(DeleteParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withExpr("face_id == \"" + faceId + "\"")
                                .build());
                    }
                } catch (Exception ignored) {
                }
            }

            // 删除人物向量和嫌疑向量
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(peopleCollectionName)
                    .withExpr("people_id == \"" + peopleId + "\"")
                    .build());
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(alertCollectionName)
                    .withExpr("people_id == \"" + peopleId + "\"")
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete person: " + peopleId, e);
        }
    }

    public PeopleRecordDTO replacePersonPhoto(String peopleId, byte[] imageBytes) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String faceId = UUID.randomUUID().toString();
        try {
            List<Float> embedding = faceSearchService.extractFeatureFromBytes(imageBytes, ".jpg");

            // 写入一条新的 face 记录，保留可追溯性
            java.util.Map<String, String> faceMetadata = new java.util.HashMap<>();
            faceMetadata.put("location", "PEOPLE_LIBRARY_REPLACE");
            faceMetadata.put("timestamp", now);
            faceMetadata.put("people_id", peopleId);
            faceMetadata.put("alert_hit", "false");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(faceId + ".jpg")
                    .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType("image/jpeg")
                    .userMetadata(faceMetadata)
                    .build());

            // 更新 people 代表图和 metadata
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .build());
            Map<String, String> meta = new java.util.HashMap<>(stat.userMetadata());
            meta.put("face_id", faceId);
            meta.put("last_time", now);
            meta.put("representative_score", "1.0");
            meta.put("representative_video_id", "MANUAL_REPLACE");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType("image/jpeg")
                    .userMetadata(meta)
                    .build());

            // face 向量保留新增
            insertEmbedding(collectionName, faceId, peopleId, embedding);
            // people 向量用新图重建（删除旧，插新）
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(peopleCollectionName)
                    .withExpr("people_id == \"" + peopleId + "\"")
                    .build());
            insertEmbedding(peopleCollectionName, faceId, peopleId, embedding);

            boolean suspect = Boolean.parseBoolean(meta.getOrDefault("is_suspect", "false"));
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(alertCollectionName)
                    .withExpr("people_id == \"" + peopleId + "\"")
                    .build());
            if (suspect) {
                insertEmbedding(alertCollectionName, faceId, peopleId, embedding);
            }

            return PeopleRecordDTO.builder()
                    .peopleId(peopleId)
                    .representativeFaceId(faceId)
                    .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                    .lastTime(now)
                    .suspect(suspect)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace photo for people_id " + peopleId, e);
        }
    }

    private void insertEmbedding(String collection, String faceId, String peopleId, List<Float> embedding) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("face_id", Collections.singletonList(faceId)));
        fields.add(new InsertParam.Field("people_id", Collections.singletonList(peopleId)));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(embedding)));
        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
    }
}
