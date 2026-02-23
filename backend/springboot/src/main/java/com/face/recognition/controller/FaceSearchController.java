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
    public ResponseEntity<List<SearchMatchDTO>> searchFace(@RequestParam("file") MultipartFile file) {
        log.info("收到人脸搜索请求，文件名: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
        try {
            List<SearchMatchDTO> results = faceSearchService.searchByImage(file);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("人脸搜索失败: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
