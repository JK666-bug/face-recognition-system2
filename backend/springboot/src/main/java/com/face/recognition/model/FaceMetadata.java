package com.face.recognition.model;

import lombok.Data;

@Data
public class FaceMetadata {
    private String id; // UUID
    private byte[] image; // image bytes
    private String timestamp; // capture time
    private String location; // capture location
}
