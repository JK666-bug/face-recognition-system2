package com.face.recognition.util;

import java.util.Map;

/**
 * MinIO user metadata flags shared by face / people objects.
 */
public final class MinioMetadataFlags {

    public static final String MANUAL_UPLOAD = "manual_upload";

    private MinioMetadataFlags() {
    }

    /**
     * True when the object was created from people-library upload / replace-photo (not surveillance capture).
     */
    public static boolean isManualUpload(Map<String, String> meta) {
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> e : meta.entrySet()) {
            String k = e.getKey();
            if (k != null && k.toLowerCase().endsWith(MANUAL_UPLOAD)) {
                return "true".equalsIgnoreCase(String.valueOf(e.getValue()).trim());
            }
        }
        return false;
    }
}
