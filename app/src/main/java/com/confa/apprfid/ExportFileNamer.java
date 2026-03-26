package com.confa.apprfid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nombres de exportación: PREFIJO_NombreEscaneo_Fecha_TIPO.xls
 */
public final class ExportFileNamer {

    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9_-]+");
    private static final int MAX_SCAN_PART_LEN = 48;

    private ExportFileNamer() {
    }

    @NonNull
    public static String sanitizeScanName(@Nullable String raw) {
        if (raw == null) {
            return "Escaneo";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "Escaneo";
        }
        String replaced = INVALID_FILE_CHARS.matcher(t).replaceAll("_");
        replaced = replaced.replaceAll("_+", "_");
        if (replaced.startsWith("_")) {
            replaced = replaced.substring(1);
        }
        if (replaced.endsWith("_")) {
            replaced = replaced.substring(0, replaced.length() - 1);
        }
        if (replaced.isEmpty()) {
            return "Escaneo";
        }
        if (replaced.length() > MAX_SCAN_PART_LEN) {
            replaced = replaced.substring(0, MAX_SCAN_PART_LEN);
        }
        return replaced;
    }

    /**
     * @param dateYyyyMmDd fecha solo día, ej. 20260324 (como en tus ejemplos)
     */
    @NonNull
    public static String buildFileName(@NonNull String sedePrefix,
            @NonNull String sanitizedScanName,
            @NonNull String dateYyyyMmDd,
            @NonNull String typeSuffix) {
        return String.format(Locale.US, "%s_%s_%s_%s.xls",
                sedePrefix, sanitizedScanName, dateYyyyMmDd, typeSuffix);
    }

    /**
     * Lectura masiva: {@code PrefijoSede_NombreArchivo_Fecha.xls} (mismo criterio que escaneo principal).
     */
    @NonNull
    public static String buildMassReadFileName(@NonNull String sedePrefix,
            @NonNull String sanitizedFileBaseName,
            @NonNull String dateYyyyMmDd) {
        return String.format(Locale.US, "%s_%s_%s.xls",
                sedePrefix, sanitizedFileBaseName, dateYyyyMmDd);
    }

    @NonNull
    public static String buildPngFileName(@NonNull String sedePrefix,
            @NonNull String sanitizedScanName,
            @NonNull String dateYyyyMmDd,
            @NonNull String typeSuffix) {
        return String.format(Locale.US, "%s_%s_%s_%s.png",
                sedePrefix, sanitizedScanName, dateYyyyMmDd, typeSuffix);
    }
}
