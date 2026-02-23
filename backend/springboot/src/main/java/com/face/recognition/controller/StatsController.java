package com.face.recognition.controller;

import com.face.recognition.dto.StatsDTO;
import com.face.recognition.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "http://localhost:5173")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @GetMapping
    public StatsDTO getDashboardStats() {
        return statsService.getStats();
    }
}
