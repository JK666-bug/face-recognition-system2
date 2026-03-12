package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsDTO {
    private long totalFaces;
    private long capturesToday;
    private String milvusStatus;
    private String minioStatus;
    private String rabbitMQStatus;
    private java.util.List<String> activeLocations;
    private java.util.List<RecentCaptureDTO> recentCaptures;
}
