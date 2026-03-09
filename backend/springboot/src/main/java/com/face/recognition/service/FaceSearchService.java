package com.face.recognition.service;

import com.face.recognition.dto.SearchMatchDTO;
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

    @Value("${spring.milvus.searchThreshold:0.90}")
    private float searchThreshold;

    @Value("${spring.minio.videoBucketName:videos}")
    private String videoBucketName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SearchMatchDTO> searchByImage(MultipartFile file) throws Exception {
        // 1. 保存临时文件
        Path tempFile = Files.createTempFile("search_", ".jpg");
        file.transferTo(tempFile.toFile());
        log.info("临时图片已保存至: {}", tempFile);

        try {
            // 2. 调用 Python 脚本提取特征
            List<Float> embedding = extractFeatureWithPython(tempFile.toAbsolutePath().toString());
            log.info("特征提取成功，维度: {}", embedding.size());

            // 3. 在 Milvus 中搜索
            List<SearchMatchDTO> results = searchInMilvus(embedding);
            log.info("Milvus 搜索完成，找到 {} 个候选结果", results.size());

            // 4. 从 MinIO 补充详细信息
            for (SearchMatchDTO match : results) {
                populateMetadataFromMinio(match);
            }

            // 5. 按照时间排序 (从近到远: 降序)
            results.sort((a, b) -> {
                if (a.getTimestamp() == null || "Unknown".equals(a.getTimestamp()))
                    return 1;
                if (b.getTimestamp() == null || "Unknown".equals(b.getTimestamp()))
                    return -1;
                return b.getTimestamp().compareTo(a.getTimestamp());
            });

            return results;
        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    private List<Float> extractFeatureWithPython(String imagePath) throws Exception {
        String projectRoot = System.getProperty("user.dir");
        // 路径适配 Windows
        String pythonExe = Paths.get(projectRoot, ".venv", "Scripts", "python.exe").toString();
        String scriptPath = Paths.get(projectRoot, "backend", "algorithms", "extract_cli.py").toString();

        ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath, imagePath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 过滤掉 tensorflow 的警告信息，只寻找最终的 JSON
                if (line.startsWith("{") && line.endsWith("}")) {
                    output.append(line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Python 脚本执行失败，退出码: {}", exitCode);
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

    private List<SearchMatchDTO> searchInMilvus(List<Float> embedding) {
        List<List<Float>> searchVectors = List.of(embedding);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(io.milvus.param.MetricType.COSINE)
                .withOutFields(List.of("face_id"))
                .withTopK(50)
                .withFloatVectors(searchVectors)
                .withVectorFieldName("vector")
                .withParams("{\"nprobe\":10}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus search failed: " + response.getMessage());
        }

        SearchResults results = response.getData();
        io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                results.getResults());

        List<SearchMatchDTO> matches = new ArrayList<>();
        List<io.milvus.response.SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        for (io.milvus.response.SearchResultsWrapper.IDScore idScore : idScores) {
            // 过滤逻辑：使用配置的相似度阈值
            if (idScore.getScore() >= searchThreshold) {
                matches.add(SearchMatchDTO.builder()
                        .id(idScore.getStrID())
                        .score(idScore.getScore())
                        .build());
            }
        }

        return matches;
    }

    private void populateMetadataFromMinio(SearchMatchDTO match) {
        try {
            String objectName = match.getId() + ".jpg";

            // 1. 获取元数据 (位置和时间)
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build());

            Map<String, String> userMetadata = stat.userMetadata();
            match.setLocation(userMetadata.getOrDefault("location", "Unknown"));
            match.setTimestamp(userMetadata.getOrDefault("timestamp", "Unknown"));

            // 1.1 获取关联视频 ID 并生成预签名 URL
            String videoId = userMetadata.get("video_id");
            if (videoId != null && !videoId.isEmpty()) {
                try {
                    String presignedUrl = minioClient.getPresignedObjectUrl(
                            io.minio.GetPresignedObjectUrlArgs.builder()
                                    .method(io.minio.http.Method.GET)
                                    .bucket(videoBucketName)
                                    .object(videoId)
                                    .expiry(2, java.util.concurrent.TimeUnit.HOURS) // 链接 2 小时有效
                                    .build());
                    match.setVideoUrl(presignedUrl);
                } catch (Exception ve) {
                    log.warn("生成视频预签名链接失败 [ID: {}]: {}", videoId, ve.getMessage());
                }
            }

            // 2. 获取图片缩略图 (转换为 Base64)
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
                byte[] bytes = stream.readAllBytes();
                match.setImageBase64(Base64.getEncoder().encodeToString(bytes));
            }

        } catch (Exception e) {
            log.warn("无法从 MinIO 获取数据 [ID: {}]: {}", match.getId(), e.getMessage());
        }
    }
}
