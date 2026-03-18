package com.face.recognition.service;

import com.face.recognition.dto.CaptureRecordDTO;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.DeleteParam;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FaceManagementService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MilvusServiceClient milvusClient;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    @Value("${spring.minio.videoBucketName:videos}")
    private String videoBucketName;

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
                            .imageBase64(base64)
                            .location(location)
                            .timestamp(timestamp)
                            .videoId(videoId)
                            .videoUrl(videoUrl)
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
}
