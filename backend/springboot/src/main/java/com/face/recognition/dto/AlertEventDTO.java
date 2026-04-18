package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventDTO {
    private String type;
    private String faceId;
    private String peopleId;
    private String alertPeopleId;
    private String location;
    private String timestamp;
    private String imageBase64;
}
