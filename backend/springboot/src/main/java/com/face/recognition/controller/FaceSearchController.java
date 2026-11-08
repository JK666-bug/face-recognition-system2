package com.face.recognition.controller;

import com.face.recognition.dto.SearchMatchDTO;
import com.face.recognition.service.FaceSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/face")
public class FaceSearchController {

    @Autowired
    private FaceSearchService faceSearchService;

    @PostMapping("/search")
    public ResponseEntity<List<SearchMatchDTO>> searchFace(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "topK", defaultValue = "50") Integer topK,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        log.info("Face search request, file: {}, topK: {}, time range: [{} - {}]", 
                file.getOriginalFilename(), 
                topK,
                startTime != null ? startTime : "any", 
                endTime != null ? endTime : "any");
        try {
            List<SearchMatchDTO> results = faceSearchService.searchByImage(file, topK, startTime, endTime);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Face search failed: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search/by-people-id")
    public ResponseEntity<List<SearchMatchDTO>> searchTrajectoryByPeopleId(
            @RequestParam("peopleId") String peopleId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        log.info("Received peopleId trajectory search: {}, range: [{} - {}]",
                peopleId,
                startTime != null ? startTime : "unlimited",
                endTime != null ? endTime : "unlimited");
        try {
            return ResponseEntity.ok(faceSearchService.searchByPeopleId(peopleId, startTime, endTime));
        } catch (Exception e) {
            log.error("peopleId trajectory search failed: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
