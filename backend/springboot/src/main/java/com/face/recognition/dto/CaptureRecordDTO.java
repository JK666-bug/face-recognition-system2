package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptureRecordDTO {
    private String id;
    private String peopleId;
    private String imageBase64;
    private String timestamp;
    private String location;
    private String videoId;
    private String videoUrl;
    private boolean alertHit;
    private String alertPeopleId;
    /** true：人物库换脸/新建写入 faces 桶的手动记录；false：MQ/抓拍链路 */
    private boolean manualUpload;
}
