package com.face.recognition.service;

import com.face.recognition.dto.StatsDTO;
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

    @Value("${spring.milvus.collectionName}")
    private String collectionName;

    public StatsDTO getStats() {
        long totalCount = 0;
        long todayCount = 0;
        String minioStatus = "Active";
        String milvusStatus = "Active";
        String rabbitStatus = "Active";

        // 1. 统计 MinIO 数量和状态
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

            LocalDate today = LocalDate.now();
            for (Result<Item> result : results) {
                Item item = result.get();
                totalCount++;

                if (item.lastModified() != null) {
                    LocalDate modifiedDate = item.lastModified().toLocalDate();
                    if (modifiedDate.equals(today)) {
                        todayCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MinIO 检查失败: {}", e.getMessage());
            minioStatus = "Error";
        }

        // 2. 检查 Milvus 状态
        try {
            R<Boolean> milvusResp = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
            if (milvusResp.getStatus() != R.Status.Success.getCode()) {
                milvusStatus = "Error";
            }
        } catch (Exception e) {
            milvusStatus = "Offline";
        }

        // 3. 检查 RabbitMQ 状态
        try {
            rabbitTemplate.execute(channel -> channel.isOpen());
        } catch (Exception e) {
            rabbitStatus = "Offline";
        }

        // 4. 获取所有活跃地点
        java.util.List<String> locations = new java.util.ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(placesBucketName).build());
            for (Result<Item> result : results) {
                locations.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.warn("获取地点列表失败: {}", e.getMessage());
        }

        return StatsDTO.builder()
                .totalFaces(totalCount)
                .capturesToday(todayCount)
                .minioStatus(minioStatus)
                .milvusStatus(milvusStatus)
                .rabbitMQStatus(rabbitStatus)
                .activeLocations(locations)
                .build();
    }
}
