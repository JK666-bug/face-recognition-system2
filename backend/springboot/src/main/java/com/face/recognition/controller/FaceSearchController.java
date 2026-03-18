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
        log.info("收到人脸搜索请求，文件名: {}, TopK: {}, 时间范围: [{} - {}]", 
                file.getOriginalFilename(), 
                topK,
                startTime != null ? startTime : "不限", 
                endTime != null ? endTime : "不限");
        try {
            List<SearchMatchDTO> results = faceSearchService.searchByImage(file, topK, startTime, endTime);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("人脸搜索失败: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
