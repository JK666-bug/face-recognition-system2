package com.face.recognition.model;

import lombok.Data;

@Data
public class FaceMetadata {
    private String id; // 唯一识别的 ID (UUID)
    private byte[] image; // 图片二进制
    private String timestamp; // 拍摄时间
    private String location; // 拍摄地点
}
