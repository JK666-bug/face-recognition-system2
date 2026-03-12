package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsDTO {
    private float captureConfidence; // 人脸抓拍置信度
    private float searchThreshold;   // 向量对比门槛
}
