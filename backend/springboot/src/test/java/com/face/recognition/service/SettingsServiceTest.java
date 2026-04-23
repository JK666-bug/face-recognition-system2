package com.face.recognition.service;

import com.face.recognition.dto.SettingsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Four threshold parameters: load defaults, persist round-trip, and non-positive clamping for people/alert.
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
    void init_loadsAllFourDefaultsWhenFileMissing() {
        assertNotNull(settingsService.getSettings());
        assertEquals(0.65f, settingsService.getSettings().getCaptureConfidence(), 0.0001f);
        assertEquals(0.70f, settingsService.getSettings().getSearchThreshold(), 0.0001f);
        assertEquals(0.80f, settingsService.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.85f, settingsService.getAlertMatchThreshold(), 0.0001f);
    }

    @Test
    @Order(2)
    void saveSettings_clampsNonPositivePeopleAndAlertThresholds() {
        SettingsDTO dto = SettingsDTO.builder()
                .captureConfidence(0.55f)
                .searchThreshold(0.66f)
                .peopleMatchThreshold(-1f)
                .alertMatchThreshold(0f)
                .build();
        settingsService.saveSettings(dto);
        assertEquals(0.80f, settingsService.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.85f, settingsService.getAlertMatchThreshold(), 0.0001f);
        assertEquals(0.55f, settingsService.getSettings().getCaptureConfidence(), 0.0001f);
        assertEquals(0.66f, settingsService.getSettings().getSearchThreshold(), 0.0001f);
    }

    @Test
    @Order(3)
    void saveSettings_persistsAllFourFieldsToJson() throws Exception {
        SettingsDTO dto = SettingsDTO.builder()
                .captureConfidence(0.61f)
                .searchThreshold(0.73f)
                .peopleMatchThreshold(0.78f)
                .alertMatchThreshold(0.88f)
                .build();
        settingsService.saveSettings(dto);

        Path configFile = Path.of(System.getProperty("user.dir"), "config", "settings.json");
        ObjectMapper mapper = new ObjectMapper();
        SettingsDTO fromDisk = mapper.readValue(Files.readString(configFile), SettingsDTO.class);
        assertEquals(0.61f, fromDisk.getCaptureConfidence(), 0.0001f);
        assertEquals(0.73f, fromDisk.getSearchThreshold(), 0.0001f);
        assertEquals(0.78f, fromDisk.getPeopleMatchThreshold(), 0.0001f);
        assertEquals(0.88f, fromDisk.getAlertMatchThreshold(), 0.0001f);
    }
}
