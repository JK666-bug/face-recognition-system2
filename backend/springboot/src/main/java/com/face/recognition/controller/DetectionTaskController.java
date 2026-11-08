package com.face.recognition.controller;

import com.face.recognition.service.DetectionTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detection")
@RequiredArgsConstructor
public class DetectionTaskController {

    private final DetectionTaskService detectionTaskService;

    @PostMapping("/tasks/webcam/start")
    public ResponseEntity<Map<String, Object>> startWebcamTask(@RequestBody(required = false) StartTaskRequest request) {
        String location = request != null ? request.getLocation() : null;
        boolean showWindow = request == null || request.getShowWindow() == null || request.getShowWindow();
        return ResponseEntity.ok(detectionTaskService.startWebcamTask(location, showWindow));
    }

    @PostMapping("/tasks/stream/start")
    public ResponseEntity<Map<String, Object>> startStreamTask(@RequestBody StartStreamTaskRequest request) {
        String location = request != null ? request.getLocation() : null;
        boolean showWindow = request != null && request.getShowWindow() != null && request.getShowWindow();
        String sourceUrl = request != null ? request.getSourceUrl() : null;
        return ResponseEntity.ok(detectionTaskService.startStreamTask(sourceUrl, location, showWindow));
    }

    @PostMapping("/tasks/{taskId}/stop")
    public ResponseEntity<Map<String, Object>> stopTask(@PathVariable String taskId) {
        return ResponseEntity.ok(detectionTaskService.stopWebcamTask(taskId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        return ResponseEntity.ok(detectionTaskService.deleteTask(taskId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> listTasks() {
        return ResponseEntity.ok(detectionTaskService.listTasks());
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(detectionTaskService.getTaskStatus(taskId));
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ResponseEntity<Map<String, Object>> getTaskLogs(
            @PathVariable String taskId,
            @RequestParam(value = "tail", defaultValue = "120") int tail) {
        return ResponseEntity.ok(detectionTaskService.getLogs(taskId, tail));
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
