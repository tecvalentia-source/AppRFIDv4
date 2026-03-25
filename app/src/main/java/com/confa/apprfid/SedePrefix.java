package com.confa.apprfid;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prefijo corto de archivo según la sede del spinner.
 */
public final class SedePrefix {

    private static final Map<String, String> BY_NORMALIZED_NAME = new HashMap<>();

    static {
        BY_NORMALIZED_NAME.put(normalizeKey("Versalles"), "VER");
        BY_NORMALIZED_NAME.put(normalizeKey("San Marcel"), "SANMAR");
        BY_NORMALIZED_NAME.put(normalizeKey("Capitalia"), "CAP");
    }

    private SedePrefix() {
    }

    @NonNull
    public static String forSedeDisplayName(@NonNull String sedeSpinnerText) {
        String k = normalizeKey(sedeSpinnerText);
        String p = BY_NORMALIZED_NAME.get(k);
        return p != null ? p : "SED";
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
