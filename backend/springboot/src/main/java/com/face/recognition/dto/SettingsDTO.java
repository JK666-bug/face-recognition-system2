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
    private float peopleMatchThreshold; // 人物底库匹配阈值（COS）
    private float alertMatchThreshold;  // 嫌疑人预警阈值（COS）
}
