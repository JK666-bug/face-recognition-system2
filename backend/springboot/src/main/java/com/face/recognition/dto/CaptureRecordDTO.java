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
    private String imageBase64;
    private String timestamp;
    private String location;
    private String videoId;
    private String videoUrl;
}
