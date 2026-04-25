package com.face.recognition.controller;

import com.face.recognition.dto.SettingsDTO;
import com.face.recognition.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    @GetMapping
    public SettingsDTO getSettings() {
        return settingsService.getSettings();
    }

    @PostMapping
    public void updateSettings(@RequestBody SettingsDTO settings) {
        settingsService.saveSettings(settings);
    }
}
