package com.face.recognition.controller;

import com.face.recognition.dto.CaptureRecordDTO;
import com.face.recognition.service.FaceManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management")
@CrossOrigin(origins = "http://localhost:5173")
public class FaceManagementController {

    @Autowired
    private FaceManagementService managementService;

    @GetMapping("/captures")
    public List<CaptureRecordDTO> getAllCaptures() {
        return managementService.listAllCaptures();
    }

    @DeleteMapping("/captures/{id}")
    public void deleteCapture(@PathVariable String id) {
        managementService.deleteCapture(id);
    }
}
