package com.face.recognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeopleRecordDTO {
    private String peopleId;
    private String representativeFaceId;
    private String imageBase64;
    private String lastTime;
    private boolean suspect;
}
