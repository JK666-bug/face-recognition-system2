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
    private float captureConfidence; // detection confidence for a capture
    private float searchThreshold;   // minimum cosine score for photo search
    private float peopleMatchThreshold; // cosine threshold for the people library
    private float alertMatchThreshold;  // cosine threshold for suspect alerts
}
