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
    private double score; // similarity score
    private String location;
    private String timestamp;
    private String imageBase64; // thumbnail for the console
    private String videoUrl; // presigned URL of the related video
    private boolean alertHit;
    private String alertPeopleId;
    /** MinIO face object: library upload / replace-photo, excluded from trajectory & image search UI */
    private boolean manualUpload;
}
