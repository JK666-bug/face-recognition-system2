package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchMatchDTO {
    private String id;
    private String peopleId;
    private double score; // 相似度分数 (距离)
    private String location;
    private String timestamp;
    private String imageBase64; // 用于前端展示的缩略图
    private String videoUrl; // 关联视频的预签名播放链接
    private boolean alertHit;
    private String alertPeopleId;
    /** MinIO face object: library upload / replace-photo, excluded from trajectory & image search UI */
    private boolean manualUpload;
}
