package com.face.recognition.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioMetadataFlagsTest {

    @Test
    void isManualUpload_falseWhenNullOrEmpty() {
        assertFalse(MinioMetadataFlags.isManualUpload(null));
        assertFalse(MinioMetadataFlags.isManualUpload(Map.of()));
    }

    @Test
    void isManualUpload_trueWhenManualFlagTrue() {
        Map<String, String> meta = new HashMap<>();
        meta.put("manual_upload", "true");
        assertTrue(MinioMetadataFlags.isManualUpload(meta));
    }

    @Test
    void isManualUpload_recognizesMinIOPrefixedKey() {
        Map<String, String> meta = Map.of("x-amz-meta-manual_upload", "TRUE");
        assertTrue(MinioMetadataFlags.isManualUpload(meta));
    }

    @Test
    void isManualUpload_falseWhenExplicitlyFalse() {
        Map<String, String> meta = Map.of("manual_upload", "false");
        assertFalse(MinioMetadataFlags.isManualUpload(meta));
    }
}
