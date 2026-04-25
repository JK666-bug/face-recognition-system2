package com.face.recognition.controller;

import com.face.recognition.service.DetectionTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/detection")
@RequiredArgsConstructor
public class DetectionTaskController {

    private final DetectionTaskService detectionTaskService;

    @PostMapping("/webcam/start")
    public ResponseEntity<Map<String, Object>> startWebcamTask(@RequestBody(required = false) StartTaskRequest request) {
        String location = request != null ? request.getLocation() : null;
        boolean showWindow = request == null || request.getShowWindow() == null || request.getShowWindow();
        return ResponseEntity.ok(detectionTaskService.startWebcamTask(location, showWindow));
    }

    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, Object>> startStreamTask(@RequestBody StartStreamTaskRequest request) {
        String location = request != null ? request.getLocation() : null;
        boolean showWindow = request != null && request.getShowWindow() != null && request.getShowWindow();
        String sourceUrl = request != null ? request.getSourceUrl() : null;
        return ResponseEntity.ok(detectionTaskService.startStreamTask(sourceUrl, location, showWindow));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopWebcamTask() {
        return ResponseEntity.ok(detectionTaskService.stopWebcamTask());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWebcamTaskStatus() {
        return ResponseEntity.ok(detectionTaskService.getStatus());
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getWebcamTaskLogs(
            @RequestParam(value = "tail", defaultValue = "120") int tail) {
        return ResponseEntity.ok(detectionTaskService.getLogs(tail));
    }

    @Data
    public static class StartTaskRequest {
        private String location;
        private Boolean showWindow;
    }

    @Data
    public static class StartStreamTaskRequest {
        private String sourceUrl;
        private String location;
        private Boolean showWindow;
    }
}
