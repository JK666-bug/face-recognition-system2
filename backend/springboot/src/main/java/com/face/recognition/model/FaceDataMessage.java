package com.face.recognition.model;

import lombok.Data;
import java.util.List;

@Data
public class FaceDataMessage {
    private List<Double> vector;
    private String image_base64;
    private String timestamp;
    private String location;
    private String video_id;
    private Double quality_score;
    private String capture_reason;
    private String type; // FACE or VIDEO
    private String video_base64; // Only for VIDEO type
}
