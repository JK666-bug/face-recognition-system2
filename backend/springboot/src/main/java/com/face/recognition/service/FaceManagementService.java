package com.face.recognition.service;

import com.face.recognition.dto.CaptureRecordDTO;
import com.face.recognition.dto.PeopleRecordDTO;
import com.face.recognition.util.MinioMetadataFlags;
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

                    // Thumbnail for the list view
                    String base64 = "";
                    try (InputStream stream = minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
                        byte[] bytes = stream.readAllBytes();
                        base64 = Base64.getEncoder().encodeToString(bytes);
                    }

                    // Related video id
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

                    boolean manualUpload = MinioMetadataFlags.isManualUpload(meta);

                    String peoplePid = meta.get("people_id");
                    if (peoplePid == null || peoplePid.isBlank()) {
                        peoplePid = meta.get("x-amz-meta-people_id");
                    }
                    SuspectAlertState suspectState = resolveSuspectAlertFromPeople(peoplePid);

                    records.add(CaptureRecordDTO.builder()
                            .id(id)
                            .peopleId(peoplePid)
                            .imageBase64(base64)
                            .location(location)
                            .timestamp(timestamp)
                            .videoId(videoId)
                            .videoUrl(videoUrl)
                            .alertHit(suspectState.hit())
                            .alertPeopleId(suspectState.alertPeopleId())
                            .manualUpload(manualUpload)
                            .build());

                } catch (Exception e) {
                    log.error("Failed to load details for object: {}", objectName);
                }
            }
            
            // Newest first
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
            deleteByFaceId(collectionName, id);
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
                Item item = result.get();
                if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                    continue;
                }
                String faceId = item.objectName().replace(".jpg", "");
                deleteByFaceId(collectionName, faceId);
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucketName).object(item.objectName()).build());
            }

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

    public List<PeopleRecordDTO> listPeopleLibrary(String peopleIdQuery) {
        List<PeopleRecordDTO> records = new ArrayList<>();
        String normalizedQuery = peopleIdQuery == null ? "" : peopleIdQuery.trim().toLowerCase();
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
                if (!normalizedQuery.isEmpty() && !peopleId.toLowerCase().contains(normalizedQuery)) {
                    continue;
                }
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

                    String lastTimeDisplay = peopleLastTimeForDisplay(meta);

                    records.add(PeopleRecordDTO.builder()
                            .peopleId(peopleId)
                            .representativeFaceId(faceIdFromPeopleMeta(meta))
                            .imageBase64(imageBase64)
                            .lastTime(lastTimeDisplay)
                            .suspect(Boolean.parseBoolean(meta.getOrDefault("is_suspect", "false")))
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to load people object {}: {}", objectName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to list people library: {}", e.getMessage());
        }
        records.sort((a, b) -> {
            String sa = a.getLastTime();
            String sb = b.getLastTime();
            boolean ea = sa == null || sa.isBlank();
            boolean eb = sb == null || sb.isBlank();
            if (ea && eb) {
                return 0;
            }
            if (ea) {
                return 1;
            }
            if (eb) {
                return -1;
            }
            return sb.compareTo(sa);
        });
        return records;
    }

    /** Show last_time maintained by captures. Empty when the person has never been captured. */
    private static String peopleLastTimeForDisplay(Map<String, String> meta) {
        if (meta == null) {
            return "";
        }
        String v = meta.get("last_time");
        if (v != null && !v.isBlank()) {
            return v;
        }
        for (Map.Entry<String, String> e : meta.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase().endsWith("last_time")) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return "";
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
        faceMetadata.put(MinioMetadataFlags.MANUAL_UPLOAD, "true");
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
        peopleMetadata.put("representative_score", "1.0");
        peopleMetadata.put("representative_video_id", "MANUAL_CREATE");
        // last_time is written only by captures. Creating a person does not set it.
        peopleMetadata.put("is_suspect", String.valueOf(suspect));
        peopleMetadata.put(MinioMetadataFlags.MANUAL_UPLOAD, "true");
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
                .lastTime("")
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
            if (!meta.containsKey(MinioMetadataFlags.MANUAL_UPLOAD)) {
                meta.put(MinioMetadataFlags.MANUAL_UPLOAD, "false");
            }

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

            // Delete then insert so the suspect collection matches the flag.
            deleteByPeopleId(alertCollectionName, peopleId);

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

    /**
     * Merge A into B (delete source A, keep target B):
     * <ul>
     *   <li>faces bucket: objects whose metadata people_id is A are retagged to B. Image bytes stay, so face_embeddings is not rewritten.</li>
     *   <li>people bucket: update B's metadata, then delete A.jpg</li>
     *   <li>Milvus people_embeddings and alert_embeddings: delete primary key A. Keep B's people row. Rewrite the alert row to match the merged suspect flag.</li>
     * </ul>
     */
    public void mergePeople(String sourcePeopleId, String targetPeopleId) {
        if (sourcePeopleId == null || sourcePeopleId.isBlank() || targetPeopleId == null || targetPeopleId.isBlank()) {
            throw new IllegalArgumentException("Both sourcePeopleId and targetPeopleId are required.");
        }
        if (sourcePeopleId.equals(targetPeopleId)) {
            throw new IllegalArgumentException("Source and target peopleId cannot be the same.");
        }

        String sourceObjectName = sourcePeopleId + ".jpg";
        String targetObjectName = targetPeopleId + ".jpg";

        try {
            PeopleObjectData sourcePeople = loadPeopleObject(sourceObjectName);
            PeopleObjectData targetPeople = loadPeopleObject(targetObjectName);

            boolean mergedSuspect = sourcePeople.suspect || targetPeople.suspect;
            List<FaceRecordData> migratedFaces = migrateFaceRecords(sourcePeopleId, targetPeopleId);

            Map<String, String> updatedTargetMeta = new java.util.HashMap<>(targetPeople.metadata);
            updatedTargetMeta.put("is_suspect", String.valueOf(mergedSuspect));
            updatedTargetMeta.put("last_time", latestTimestamp(
                    sourcePeople.metadata.get("last_time"),
                    targetPeople.metadata.get("last_time")));
            boolean mergedManualOnly = MinioMetadataFlags.isManualUpload(sourcePeople.metadata)
                    && MinioMetadataFlags.isManualUpload(targetPeople.metadata);
            updatedTargetMeta.put(MinioMetadataFlags.MANUAL_UPLOAD, String.valueOf(mergedManualOnly));

            writeObject(peopleBucketName, targetObjectName, targetPeople.imageBytes, "image/jpeg", updatedTargetMeta);

            // B's representative image did not change, so B's vector stays. Delete A only. Do not reuse A's vector for B.
            deleteByPeopleId(peopleCollectionName, sourcePeopleId);

            deleteByPeopleId(alertCollectionName, sourcePeopleId);
            if (mergedSuspect) {
                if (!targetPeople.suspect && sourcePeople.suspect) {
                    // B becomes a suspect in this merge. Embed B's current representative image.
                    List<Float> alertEmb = faceSearchService.extractFeatureFromBytes(targetPeople.imageBytes, ".jpg");
                    insertEmbedding(alertCollectionName, null, targetPeopleId, alertEmb);
                }
                // B was already a suspect. Keep B's alert row. A was deleted above.
            } else {
                deleteByPeopleId(alertCollectionName, targetPeopleId);
            }

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(sourceObjectName)
                    .build());

            log.info("Merged people {} into {}. Migrated {} face records. Suspect state: {}",
                    sourcePeopleId, targetPeopleId, migratedFaces.size(), mergedSuspect);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to merge people " + sourcePeopleId + " into " + targetPeopleId, e);
        }
    }

    public void deletePerson(String peopleId) {
        String peopleObjectName = peopleId + ".jpg";
        try {
            // Delete the people-bucket object
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleObjectName)
                    .build());

            // Delete every capture image for this person
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
                    String pid = peopleIdFromFaceMeta(stat.userMetadata());
                    if (peopleId.equals(pid)) {
                        String faceId = item.objectName().replace(".jpg", "");
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(item.objectName())
                                .build());
                        deleteByFaceId(collectionName, faceId);
                    }
                } catch (Exception ignored) {
                }
            }

            // Delete people and alert vectors. Face vectors were already deleted by face_id.
            deleteByPeopleId(peopleCollectionName, peopleId);
            deleteByPeopleId(alertCollectionName, peopleId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete person: " + peopleId, e);
        }
    }

    public PeopleRecordDTO replacePersonPhoto(String peopleId, byte[] imageBytes) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String faceId = UUID.randomUUID().toString();
        try {
            List<Float> embedding = faceSearchService.extractFeatureFromBytes(imageBytes, ".jpg");

            // Write a new face record so the change stays traceable
            java.util.Map<String, String> faceMetadata = new java.util.HashMap<>();
            faceMetadata.put("location", "PEOPLE_LIBRARY_REPLACE");
            faceMetadata.put("timestamp", now);
            faceMetadata.put("people_id", peopleId);
            faceMetadata.put(MinioMetadataFlags.MANUAL_UPLOAD, "true");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(faceId + ".jpg")
                    .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType("image/jpeg")
                    .userMetadata(faceMetadata)
                    .build());

            // Update the representative image and metadata
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .build());
            Map<String, String> meta = new java.util.HashMap<>(stat.userMetadata());
            meta.put("face_id", faceId);
            // Same manual-upload flag as a people-library create, so stats and filters stay consistent
            meta.put(MinioMetadataFlags.MANUAL_UPLOAD, "true");
            // last_time is updated only by captures, not by a photo replace
            meta.put("representative_score", "1.0");
            meta.put("representative_video_id", "MANUAL_REPLACE");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType("image/jpeg")
                    .userMetadata(meta)
                    .build());

            // Insert the new face vector
            insertEmbedding(collectionName, faceId, peopleId, embedding);
            // people and alert rows store only people_id and the vector
            deleteByPeopleId(peopleCollectionName, peopleId);
            insertEmbedding(peopleCollectionName, faceId, peopleId, embedding);

            boolean suspect = Boolean.parseBoolean(meta.getOrDefault("is_suspect", "false"));
            deleteByPeopleId(alertCollectionName, peopleId);
            if (suspect) {
                insertEmbedding(alertCollectionName, faceId, peopleId, embedding);
            }

            return PeopleRecordDTO.builder()
                    .peopleId(peopleId)
                    .representativeFaceId(faceId)
                    .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                    .lastTime(meta.getOrDefault("last_time", ""))
                    .suspect(suspect)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace photo for people_id " + peopleId, e);
        }
    }

    private void insertEmbedding(String collection, String faceId, String peopleId, List<Float> embedding) {
        if (collection.equals(peopleCollectionName) || collection.equals(alertCollectionName)) {
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("people_id", Collections.singletonList(peopleId)));
            fields.add(new InsertParam.Field("vector", Collections.singletonList(embedding)));
            milvusClient.insert(InsertParam.newBuilder()
                    .withCollectionName(collection)
                    .withFields(fields)
                    .build());
            return;
        }
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("face_id", Collections.singletonList(faceId)));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(embedding)));
        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
    }

    private static String peopleIdFromFaceMeta(Map<String, String> meta) {
        if (meta == null) {
            return null;
        }
        String pid = meta.get("people_id");
        if (pid == null || pid.isBlank()) {
            pid = meta.get("x-amz-meta-people_id");
        }
        return pid;
    }

    private static String faceIdFromPeopleMeta(Map<String, String> meta) {
        if (meta == null) {
            return null;
        }
        String fid = meta.get("face_id");
        if (fid == null || fid.isBlank()) {
            fid = meta.get("x-amz-meta-face_id");
        }
        return fid;
    }

    private List<FaceRecordData> migrateFaceRecords(String sourcePeopleId, String targetPeopleId) throws Exception {
        List<FaceRecordData> migrated = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                continue;
            }

            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(item.objectName())
                    .build());
            Map<String, String> metadata = new java.util.HashMap<>(stat.userMetadata());
            String recordPeopleId = peopleIdFromFaceMeta(metadata);
            if (!sourcePeopleId.equals(recordPeopleId)) {
                continue;
            }

            byte[] imageBytes;
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(item.objectName())
                    .build())) {
                imageBytes = stream.readAllBytes();
            }

            String faceId = item.objectName().replace(".jpg", "");
            metadata.put("people_id", targetPeopleId);
            writeObject(bucketName, item.objectName(), imageBytes, "image/jpeg", metadata);

            // face_embeddings stores face_id and the vector. Unchanged pixels keep the same vector.
            migrated.add(new FaceRecordData(faceId, imageBytes, metadata));
        }

        return migrated;
    }

    private PeopleObjectData loadPeopleObject(String objectName) throws Exception {
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(peopleBucketName)
                .object(objectName)
                .build());
        byte[] imageBytes;
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(peopleBucketName)
                .object(objectName)
                .build())) {
            imageBytes = stream.readAllBytes();
        }
        Map<String, String> metadata = new java.util.HashMap<>(stat.userMetadata());
        boolean suspect = Boolean.parseBoolean(metadata.getOrDefault("is_suspect", "false"));
        return new PeopleObjectData(objectName.replace(".jpg", ""), imageBytes, metadata, suspect);
    }

    private static String escapeMilvusStr(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Alert state comes from is_suspect on the people object, not from alert fields on a face object.
     */
    private SuspectAlertState resolveSuspectAlertFromPeople(String peopleId) {
        if (peopleId == null || peopleId.isBlank()) {
            return SuspectAlertState.none();
        }
        try {
            StatObjectResponse pstat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(peopleBucketName)
                    .object(peopleId + ".jpg")
                    .build());
            boolean suspect = Boolean.parseBoolean(
                    pstat.userMetadata().getOrDefault("is_suspect", "false"));
            return suspect ? new SuspectAlertState(true, peopleId) : SuspectAlertState.none();
        } catch (Exception e) {
            log.debug("resolveSuspectAlertFromPeople skip for {}: {}", peopleId, e.getMessage());
            return SuspectAlertState.none();
        }
    }

    private void writeObject(String bucket, String objectName, byte[] bytes, String contentType, Map<String, String> metadata)
            throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(contentType)
                .userMetadata(metadata)
                .build());
    }

    private void deleteByPeopleId(String collection, String peopleId) {
        if (peopleId == null || peopleId.isBlank()) {
            return;
        }
        String esc = escapeMilvusStr(peopleId);
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("people_id in [\"" + esc + "\"]")
                .build());
    }

    /**
     * Milvus 2.3+ deletes a primary key with an in expression. face_id == "..." raises invalid plan node type.
     */
    private void deleteByFaceId(String collection, String faceId) {
        if (faceId == null || faceId.isBlank()) {
            return;
        }
        String escaped = faceId.replace("\\", "\\\\").replace("\"", "\\\"");
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("face_id in [\"" + escaped + "\"]")
                .build());
    }

    private String latestTimestamp(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }

    private record PeopleObjectData(String peopleId, byte[] imageBytes, Map<String, String> metadata, boolean suspect) {
    }

    private record FaceRecordData(String faceId, byte[] imageBytes, Map<String, String> metadata) {
    }

    private record SuspectAlertState(boolean hit, String alertPeopleId) {
        static SuspectAlertState none() {
            return new SuspectAlertState(false, null);
        }
    }
}
