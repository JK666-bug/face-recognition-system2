package com.face.recognition.service;

import com.face.recognition.dto.SearchMatchDTO;
import com.face.recognition.util.MinioMetadataFlags;
import com.face.recognition.util.ProjectRoot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FaceSearchService {

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private MinioClient minioClient;

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    @Value("${spring.minio.peopleBucketName:people}")
    private String peopleBucketName;

    @Value("${spring.milvus.searchThreshold:0.90}")
    private float searchThreshold;

    @Value("${spring.minio.videoBucketName:videos}")
    private String videoBucketName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SearchMatchDTO> searchByImage(MultipartFile file, Integer topK, String startTime, String endTime) throws Exception {
        // 1. Write a temp file
        Path tempFile = Files.createTempFile("search_", ".jpg");
        file.transferTo(tempFile.toFile());
        log.info("Temporary image saved to: {}", tempFile);

        try {
            // 2. Ask the Python script for an embedding
            List<Float> embedding = extractFeatureWithPython(tempFile.toAbsolutePath().toString());
            log.info("Embedding extracted, dimensions: {}", embedding.size());

            // 3. Search Milvus
            List<SearchMatchDTO> results = searchInMilvus(embedding, topK);
            log.info("Milvus search returned {} candidates", results.size());

            // 4. Fill details from MinIO and drop missing objects
            List<SearchMatchDTO> responseList = new ArrayList<>();
            for (SearchMatchDTO match : results) {
                populateMetadataFromMinio(match);
                
                // Skip a vector whose image is already gone from MinIO
                if (match.getImageBase64() == null || match.getImageBase64().isEmpty()) {
                    log.warn("Skipping orphan vector. Image missing in MinIO: {}", match.getId());
                    continue;
                }
                if (match.isManualUpload()) {
                    continue;
                }

                // Time-range filter
                if (isWithinTimeRange(match.getTimestamp(), startTime, endTime)) {
                    responseList.add(match);
                }
            }
            log.info("Results after the time filter: {}", responseList.size());

            // 5. Newest first
            responseList.sort((a, b) -> {
                if (a.getTimestamp() == null || "Unknown".equals(a.getTimestamp()))
                    return 1;
                if (b.getTimestamp() == null || "Unknown".equals(b.getTimestamp()))
                    return -1;
                return b.getTimestamp().compareTo(a.getTimestamp());
            });

            return responseList;
        } finally {
            // Delete the temp file
            Files.deleteIfExists(tempFile);
        }
    }

    public List<Float> extractFeatureFromUpload(MultipartFile file) throws Exception {
        Path tempFile = Files.createTempFile("people_upload_", ".jpg");
        file.transferTo(tempFile.toFile());
        try {
            return extractFeatureWithPython(tempFile.toAbsolutePath().toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public List<Float> extractFeatureFromBytes(byte[] imageBytes, String suffix) throws Exception {
        Path tempFile = Files.createTempFile("people_bytes_", suffix == null ? ".jpg" : suffix);
        Files.write(tempFile, imageBytes);
        try {
            return extractFeatureWithPython(tempFile.toAbsolutePath().toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public List<SearchMatchDTO> searchByPeopleId(String peopleId, String startTime, String endTime) {
        if (peopleId == null || peopleId.isBlank()) {
            return List.of();
        }

        List<SearchMatchDTO> results = new ArrayList<>();
        try {
            Iterable<io.minio.Result<io.minio.messages.Item>> objects = minioClient.listObjects(
                    io.minio.ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

            for (io.minio.Result<io.minio.messages.Item> objectResult : objects) {
                io.minio.messages.Item item = objectResult.get();
                if (item.isDir() || !item.objectName().endsWith(".jpg")) {
                    continue;
                }

                try {
                    String id = item.objectName().replace(".jpg", "");
                    StatObjectResponse stat = minioClient.statObject(
                            StatObjectArgs.builder().bucket(bucketName).object(item.objectName()).build());
                    Map<String, String> meta = stat.userMetadata();
                    String recordPeopleId = meta.getOrDefault("people_id",
                            meta.getOrDefault("x-amz-meta-people_id", ""));
                    if (!peopleId.equals(recordPeopleId)) {
                        continue;
                    }
                    if (MinioMetadataFlags.isManualUpload(meta)) {
                        continue;
                    }

                    SearchMatchDTO dto = SearchMatchDTO.builder()
                            .id(id)
                            .peopleId(recordPeopleId)
                            .build();
                    populateMetadataFromMinio(dto);

                    if (dto.getImageBase64() == null || dto.getImageBase64().isEmpty()) {
                        continue;
                    }

                    if (isWithinTimeRange(dto.getTimestamp(), startTime, endTime)) {
                        results.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load capture for peopleId {}: {}", peopleId, e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to search trajectory by peopleId: " + peopleId, e);
        }

        results.sort((a, b) -> {
            if (a.getTimestamp() == null || "Unknown".equals(a.getTimestamp())) {
                return 1;
            }
            if (b.getTimestamp() == null || "Unknown".equals(b.getTimestamp())) {
                return -1;
            }
            return b.getTimestamp().compareTo(a.getTimestamp());
        });
        return results;
    }

    private boolean isWithinTimeRange(String timestamp, String start, String end) {
        // No time filter means every row passes
        if ((start == null || start.isEmpty()) && (end == null || end.isEmpty())) {
            return true;
        }
        
        // An unknown capture time is excluded when a filter is set
        if (timestamp == null || "Unknown".equals(timestamp)) {
            return false;
        }

        try {
            // The console sends "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd"
            // Python stores "yyyy-MM-dd HH:mm:ss"
            // The format sorts correctly as text
            
            boolean startOk = true;
            if (start != null && !start.isEmpty()) {
                // A date-only start becomes YYYY-MM-DD 00:00:00
                String fullStart = start.length() == 10 ? start + " 00:00:00" : start;
                startOk = timestamp.compareTo(fullStart) >= 0;
            }

            boolean endOk = true;
            if (end != null && !end.isEmpty()) {
                // A date-only end becomes YYYY-MM-DD 23:59:59
                String fullEnd = end.length() == 10 ? end + " 23:59:59" : end;
                endOk = timestamp.compareTo(fullEnd) <= 0;
            }

            return startOk && endOk;
        } catch (Exception e) {
            log.warn("Timestamp comparison failed for: {}, error: {}", timestamp, e.getMessage());
            return false;
        }
    }

    private List<Float> extractFeatureWithPython(String imagePath) throws Exception {
        Path projectRoot = ProjectRoot.resolve();
        String pythonExe = ProjectRoot.pythonExecutable(projectRoot);
        Path script = projectRoot.resolve("backend").resolve("algorithms").resolve("extract_cli.py");
        if (!Files.isRegularFile(script)) {
            throw new RuntimeException("extract_cli.py not found. Start Spring Boot from the repository root. cwd="
                    + System.getProperty("user.dir"));
        }
        log.info("Feature extraction using python={} script={}", pythonExe, script);

        ProcessBuilder pb = new ProcessBuilder(pythonExe, script.toString(), imagePath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ignore TensorFlow warnings and keep the final JSON line
                if (line.startsWith("{") && line.endsWith("}")) {
                    output.append(line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Python script failed with exit code: {}", exitCode);
            throw new RuntimeException("Feature extraction failed: " + output.toString());
        }

        JsonNode node = objectMapper.readTree(output.toString());
        if (node.has("error")) {
            throw new RuntimeException("Python Error: " + node.get("error").asText());
        }

        List<Float> embedding = new ArrayList<>();
        JsonNode embeddingNode = node.get("embedding");
        for (JsonNode val : embeddingNode) {
            embedding.add((float) val.asDouble());
        }
        return embedding;
    }

    @Autowired
    private SettingsService settingsService;

    private List<SearchMatchDTO> searchInMilvus(List<Float> embedding, Integer topK) {
        List<List<Float>> searchVectors = List.of(embedding);

        R<SearchResults> response = runSearch(topK, searchVectors);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus search failed: " + response.getMessage());
        }

        SearchResults results = response.getData();
        io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                results.getResults());

        List<SearchMatchDTO> matches = new ArrayList<>();
        List<io.milvus.response.SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        float threshold = settingsService.getSearchThreshold();
        for (io.milvus.response.SearchResultsWrapper.IDScore idScore : idScores) {
            // Keep scores at or above the configured threshold
            if (idScore.getScore() >= threshold) {
                matches.add(SearchMatchDTO.builder()
                        .id(idScore.getStrID())
                        .peopleId(null)
                        .score(idScore.getScore())
                        .build());
            }
        }

        return matches;
    }

    private void populateMetadataFromMinio(SearchMatchDTO match) {
        try {
            String objectName = match.getId() + ".jpg";

            // 1. Read location and time from metadata
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build());

            Map<String, String> meta = stat.userMetadata();
            String location = meta.getOrDefault("location", 
                              meta.getOrDefault("location".toLowerCase(), 
                              meta.getOrDefault("x-amz-meta-location", "Unknown")));
            
            String timestamp = meta.getOrDefault("timestamp", 
                               meta.getOrDefault("timestamp".toLowerCase(), 
                               meta.getOrDefault("x-amz-meta-timestamp", "Unknown")));
            
            match.setLocation(location);
            match.setTimestamp(timestamp);
            String pid = meta.get("people_id");
            if (pid == null || pid.isBlank()) {
                pid = meta.get("x-amz-meta-people_id");
            }
            match.setPeopleId(pid != null && !pid.isBlank() ? pid : match.getPeopleId());
            match.setManualUpload(MinioMetadataFlags.isManualUpload(meta));
            applyPeopleAlertState(match);

            // 1.1 Build a presigned URL for the related video
            String videoId = meta.get("video_id");
            if (videoId == null) videoId = meta.get("video-id");
            if (videoId == null) videoId = meta.get("x-amz-meta-video_id");
            if (videoId == null) videoId = meta.get("x-amz-meta-video-id");
            
            if (videoId != null && !videoId.isEmpty()) {
                log.debug("Found videoId in metadata: {}", videoId);
                try {
                    String presignedUrl = minioClient.getPresignedObjectUrl(
                            io.minio.GetPresignedObjectUrlArgs.builder()
                                    .method(io.minio.http.Method.GET)
                                    .bucket(videoBucketName)
                                    .object(videoId)
                                    .expiry(2, java.util.concurrent.TimeUnit.HOURS)
                                    .build());
                    match.setVideoUrl(presignedUrl);
                } catch (Exception ve) {
                    log.warn("Failed to presign video {}: {}", videoId, ve.getMessage());
                }
            } else {
                log.debug("No videoId found in metadata for capture: {}", match.getId());
            }

            // 2. Load the thumbnail and encode it as Base64
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
                byte[] bytes = stream.readAllBytes();
                match.setImageBase64(Base64.getEncoder().encodeToString(bytes));
            }

        } catch (Exception e) {
            log.warn("Cannot read MinIO object {}: {}", match.getId(), e.getMessage());
        }
    }

    private void applyPeopleAlertState(SearchMatchDTO match) {
        String peopleId = match.getPeopleId();
        if (peopleId == null || peopleId.isBlank()) {
            match.setAlertHit(false);
            match.setAlertPeopleId(null);
            return;
        }

        try {
            StatObjectResponse peopleStat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(peopleBucketName)
                            .object(peopleId + ".jpg")
                            .build());
            boolean suspect = Boolean.parseBoolean(
                    peopleStat.userMetadata().getOrDefault("is_suspect", "false"));
            match.setAlertHit(suspect);
            match.setAlertPeopleId(suspect ? peopleId : null);
        } catch (Exception e) {
            match.setAlertHit(false);
            match.setAlertPeopleId(null);
            log.debug("Failed to resolve people alert state for {}: {}", peopleId, e.getMessage());
        }
    }

    private R<SearchResults> runSearch(Integer topK, List<List<Float>> searchVectors) {
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(io.milvus.param.MetricType.COSINE)
                .withTopK(topK != null ? topK : 50)
                .withFloatVectors(searchVectors)
                .withVectorFieldName("vector")
                .withParams("{\"nprobe\":10}")
                .withOutFields(List.of("face_id"));
        return milvusClient.search(builder.build());
    }
}
