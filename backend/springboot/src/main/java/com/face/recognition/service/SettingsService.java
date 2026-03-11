package com.face.recognition.service;

import com.face.recognition.dto.SettingsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Slf4j
@Service
public class SettingsService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SettingsDTO currentSettings;
    private String configPath;

    @PostConstruct
    public void init() {
        String projectRoot = System.getProperty("user.dir");
        configPath = Paths.get(projectRoot, "config", "settings.json").toString();
        
        File configFile = new File(configPath);
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        if (configFile.exists()) {
            try {
                currentSettings = objectMapper.readValue(configFile, SettingsDTO.class);
                log.info("Loaded system settings from: {}", configPath);
            } catch (IOException e) {
                log.error("Failed to load settings, using defaults: {}", e.getMessage());
                loadDefaults();
            }
        } else {
            loadDefaults();
            saveSettings(currentSettings);
        }
    }

    private void loadDefaults() {
        currentSettings = SettingsDTO.builder()
                .captureConfidence(0.65f)
                .searchThreshold(0.70f)
                .build();
    }

    public SettingsDTO getSettings() {
        return currentSettings;
    }

    public void saveSettings(SettingsDTO settings) {
        this.currentSettings = settings;
        try {
            objectMapper.writeValue(new File(configPath), settings);
            log.info("System settings updated and saved to: {}", configPath);
        } catch (IOException e) {
            log.error("Failed to save settings: {}", e.getMessage());
        }
    }

    public float getSearchThreshold() {
        return currentSettings.getSearchThreshold();
    }
}
