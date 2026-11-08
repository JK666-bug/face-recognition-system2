package com.face.recognition.service;

import com.face.recognition.dto.SettingsDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Threshold fallback and loading. Tests point user.dir at a temp directory.
 */
@SpringJUnitConfig(classes = SettingsService.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettingsServiceTest {

    private static String originalUserDir;

    @BeforeAll
    static void redirectUserDir(@TempDir Path tempHome) {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempHome.toAbsolutePath().toString());
    }

    @AfterAll
    static void restoreUserDir() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Autowired
    private SettingsService settingsService;

    @Test
    @Order(1)
    void init_loadsDefaultsWhenFileMissing() {
        assertNotNull(settingsService.getSettings());
        assertEquals(0.80f, settingsService.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.85f, settingsService.getAlertMatchThreshold(), 0.0001f);
    }

    @Test
    @Order(2)
    void saveSettings_clampsNonPositiveThresholds() {
        SettingsDTO dto = SettingsDTO.builder()
                .captureConfidence(0.5f)
                .searchThreshold(0.6f)
                .peopleMatchThreshold(-1f)
                .alertMatchThreshold(0f)
                .build();
        settingsService.saveSettings(dto);
        assertEquals(0.80f, settingsService.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.85f, settingsService.getAlertMatchThreshold(), 0.0001f);
    }

    @Test
    @Order(3)
    void saveSettings_keepsPositiveThresholds() {
        SettingsDTO dto = SettingsDTO.builder()
                .captureConfidence(0.65f)
                .searchThreshold(0.70f)
                .peopleMatchThreshold(0.72f)
                .alertMatchThreshold(0.66f)
                .build();
        settingsService.saveSettings(dto);
        assertEquals(0.72f, settingsService.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.66f, settingsService.getAlertMatchThreshold(), 0.0001f);
    }
}
