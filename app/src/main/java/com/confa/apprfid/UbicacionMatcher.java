package com.confa.apprfid;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Compara la sede elegida en la app con la columna "Ubicación" del maestro (trim, sin distinguir mayúsculas).
 * Acepta coincidencia exacta, prefijo seguido de separador (p. ej. "Capitalia" = "Capitalia - Bodega")
 * y la sede como subcadena delimitada por no alfanuméricos (p. ej. "San Marcel" dentro del texto).
 */
public final class UbicacionMatcher {

    private UbicacionMatcher() {
    }

    public static boolean matchesSelectedSede(@Nullable String ubicacionEnMaestro, @NonNull String selectedSede) {
        String sel = normalizeToken(selectedSede);
        if (sel.isEmpty()) {
            return false;
        }
        String ubi = normalizeToken(ubicacionEnMaestro);
        if (ubi.isEmpty()) {
            return false;
        }
        if (sel.equalsIgnoreCase(ubi)) {
            return true;
        }
        if (ubi.length() >= sel.length() && ubi.regionMatches(true, 0, sel, 0, sel.length())) {
            if (ubi.length() == sel.length()) {
                return true;
            }
            char c = ubi.charAt(sel.length());
            if (!Character.isLetterOrDigit(c)) {
                return true;
            }
        }
        return containsAsBoundedPhrase(ubi, sel);
    }

    /** La sede aparece en el texto rodeada de límites no alfanuméricos (inicio/fin cuentan como límite). */
    private static boolean containsAsBoundedPhrase(@NonNull String ubi, @NonNull String sel) {
        if (sel.isEmpty()) {
            return false;
        }
        String u = ubi.toLowerCase(Locale.ROOT);
        String s = sel.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= u.length() - s.length()) {
            int idx = u.indexOf(s, from);
            if (idx < 0) {
                return false;
            }
            char before = idx > 0 ? u.charAt(idx - 1) : '\0';
            int afterPos = idx + s.length();
            char after = afterPos < u.length() ? u.charAt(afterPos) : '\0';
            boolean okBefore = idx == 0 || !Character.isLetterOrDigit(before);
            boolean okAfter = afterPos >= u.length() || !Character.isLetterOrDigit(after);
            if (okBefore && okAfter) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }

    @NonNull
    public static String normalizeToken(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
