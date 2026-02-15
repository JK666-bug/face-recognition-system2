package com.face.recognition.model;

import lombok.Data;
import java.util.List;

@Data
public class FaceDataMessage {
    private List<Double> vector;
    private String image_base64;
    private String timestamp;
    private String location;
}
