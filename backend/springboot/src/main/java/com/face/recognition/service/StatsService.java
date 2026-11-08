package com.face.recognition.service;

import com.face.recognition.dto.RecentCaptureDTO;
import com.face.recognition.dto.StatsDTO;
import com.face.recognition.util.MinioMetadataFlags;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
public class StatsService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    @Value("${spring.minio.placesBucketName}")
    private String placesBucketName;

    @Value("${spring.minio.peopleBucketName:people}")
    private String peopleBucketName;

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    public StatsDTO getStats() {
        long totalCount = 0;
        long todayCount = 0;
        long totalPeople = 0;
        long peopleToday = 0;
        String minioStatus = "Active";
        String milvusStatus = "Active";
        String rabbitStatus = "Active";

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

            LocalDate today = LocalDate.now();
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                    continue;
                }
                try {
                    io.minio.StatObjectResponse stat = minioClient.statObject(
                            io.minio.StatObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(item.objectName())
                                    .build());
                    Map<String, String> meta = stat.userMetadata();
                    if (MinioMetadataFlags.isManualUpload(meta)) {
                        continue;
                    }
                    totalCount++;
                    LocalDate captureDay = parseCaptureDateFromMeta(meta, item);
                    if (today.equals(captureDay)) {
                        todayCount++;
                    }
                } catch (Exception e) {
                    log.debug("Skip face object in stats (stat failed): {} — {}", item.objectName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to inspect faces bucket: {}", e.getMessage());
            minioStatus = "Error";
        }

        try {
            Iterable<Result<Item>> peopleResults = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(peopleBucketName).recursive(true).build());

            LocalDate today = LocalDate.now();
            for (Result<Item> result : peopleResults) {
                Item item = result.get();
                if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                    continue;
                }

                try {
                    io.minio.StatObjectResponse stat = minioClient.statObject(
                            io.minio.StatObjectArgs.builder()
                                    .bucket(peopleBucketName)
                                    .object(item.objectName())
                                    .build());
                    Map<String, String> meta = stat.userMetadata();
                    if (MinioMetadataFlags.isManualUpload(meta)) {
                        continue;
                    }
                    totalPeople++;
                    LocalDate activityDay = parsePeopleActivityDateFromMeta(meta, item);
                    if (today.equals(activityDay)) {
                        peopleToday++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to read people metadata for {}: {}", item.objectName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to inspect people bucket: {}", e.getMessage());
            minioStatus = "Error";
        }

        try {
            R<Boolean> milvusResp = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
            if (milvusResp.getStatus() != R.Status.Success.getCode()) {
                milvusStatus = "Error";
            }
        } catch (Exception e) {
            milvusStatus = "Offline";
        }

        try {
            rabbitTemplate.execute(channel -> channel.isOpen());
        } catch (Exception e) {
            rabbitStatus = "Offline";
        }

        java.util.List<String> locations = new java.util.ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(placesBucketName).build());
            for (Result<Item> result : results) {
                locations.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.warn("Failed to load locations: {}", e.getMessage());
        }

        java.util.List<RecentCaptureDTO> recentCaptures = new java.util.ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

            java.util.List<Item> allItems = new java.util.ArrayList<>();
            for (Result<Item> res : objects) {
                allItems.add(res.get());
            }

            allItems.sort((a, b) -> b.lastModified().compareTo(a.lastModified()));

            int limit = Math.min(allItems.size(), 6);
            for (int i = 0; i < limit; i++) {
                Item item = allItems.get(i);
                try {
                    io.minio.StatObjectResponse stat = minioClient.statObject(
                            io.minio.StatObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(item.objectName())
                                    .build());

                    java.util.Map<String, String> meta = stat.userMetadata();
                    if (MinioMetadataFlags.isManualUpload(meta)) {
                        continue;
                    }
                    String location = meta.getOrDefault(
                            "location",
                            meta.getOrDefault("location".toLowerCase(),
                                    meta.getOrDefault("x-amz-meta-location", "Unknown")));

                    String timestamp = meta.getOrDefault(
                            "timestamp",
                            meta.getOrDefault("timestamp".toLowerCase(),
                                    meta.getOrDefault("x-amz-meta-timestamp", item.lastModified().toString())));

                    try (java.io.InputStream stream = minioClient.getObject(
                            io.minio.GetObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(item.objectName())
                                    .build())) {
                        byte[] bytes = stream.readAllBytes();
                        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);

                        recentCaptures.add(RecentCaptureDTO.builder()
                                .id(item.objectName())
                                .imageBase64(base64)
                                .location(location)
                                .timestamp(timestamp)
                                .build());
                    }
                } catch (Exception e) {
                    log.error("Failed to load capture detail [{}]: {}", item.objectName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load recent captures: {}", e.getMessage());
        }

        return StatsDTO.builder()
                .totalFaces(totalCount)
                .capturesToday(todayCount)
                .totalPeople(totalPeople)
                .peopleToday(peopleToday)
                .minioStatus(minioStatus)
                .milvusStatus(milvusStatus)
                .rabbitMQStatus(rabbitStatus)
                .activeLocations(locations)
                .recentCaptures(recentCaptures)
                .build();
    }

    /**
     * Captures today use metadata timestamp. If it cannot be parsed, fall back to lastModified.
     */
    private LocalDate parseCaptureDateFromMeta(Map<String, String> meta, Item item) {
        String ts = firstMeta(meta, "timestamp");
        if (ts != null && ts.length() >= 10) {
            try {
                return LocalDate.parse(ts.substring(0, 10));
            } catch (Exception ignored) {
            }
        }
        if (item.lastModified() != null) {
            return item.lastModified().toLocalDate();
        }
        return null;
    }

    /**
     * People active today use metadata last_time. If it cannot be parsed, fall back to lastModified.
     */
    private LocalDate parsePeopleActivityDateFromMeta(Map<String, String> meta, Item item) {
        String lt = firstMeta(meta, "last_time");
        if (lt != null && lt.length() >= 10) {
            try {
                return LocalDate.parse(lt.substring(0, 10));
            } catch (Exception ignored) {
            }
        }
        if (item.lastModified() != null) {
            return item.lastModified().toLocalDate();
        }
        return null;
    }

    private static String firstMeta(Map<String, String> meta, String key) {
        if (meta == null) {
            return null;
        }
        String v = meta.get(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        String lower = key.toLowerCase();
        for (Map.Entry<String, String> e : meta.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase().endsWith(lower)) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
