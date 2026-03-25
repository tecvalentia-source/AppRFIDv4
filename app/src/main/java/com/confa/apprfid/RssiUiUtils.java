package com.confa.apprfid;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSSI en cadena (API Chainway / variantes) → dBm y proximidad 0–100%.
 */
public final class RssiUiUtils {

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /** dBm considerado “muy cerca” (100%). */
    private static final int RSSI_NEAR = -38;
    /** dBm considerado “muy lejos” (0%). */
    private static final int RSSI_FAR = -95;

    private RssiUiUtils() {
    }

    public static int parseRssiDbm(@NonNull String rssi) {
        if (rssi.isEmpty()) {
            return RSSI_FAR;
        }
        String s = rssi.trim();
        s = s.replaceFirst("(?i)^rssi\\s*[:=]\\s*", "");
        s = s.replace("dBm", "").replace("dB", "").trim();

        Matcher m = FIRST_NUMBER.matcher(s);
        if (m.find()) {
            try {
                float f = Float.parseFloat(m.group());
                int v = Math.round(f);
                String token = m.group();
                if (v > 0 && v < 100 && (token == null || !token.startsWith("-"))) {
                    v = -Math.abs(v);
                }
                if (v > 0) {
                    v = -Math.min(v, 40);
                }
                return clampDbm(v);
            } catch (NumberFormatException ignored) {
            }
        }

        String hex = s;
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2).trim();
        }
        if (hex.matches("[0-9A-Fa-f]{1,4}")) {
            try {
                int u = Integer.parseInt(hex, 16);
                int bits = hex.length() <= 2 ? 8 : 16;
                int mask = (1 << bits) - 1;
                u &= mask;
                int signBit = 1 << (bits - 1);
                if ((u & signBit) != 0) {
                    u |= ~mask;
                }
                return clampDbm(u);
            } catch (NumberFormatException ignored) {
            }
        }

        return RSSI_FAR;
    }

    private static int clampDbm(int dbm) {
        if (dbm > 0) {
            dbm = 0;
        }
        if (dbm < -130) {
            dbm = -130;
        }
        return dbm;
    }

    /** 0–100 (100 = muy cerca). */
    public static int proximityPercent(int rssiDbm) {
        if (rssiDbm >= RSSI_NEAR) {
            return 100;
        }
        if (rssiDbm <= RSSI_FAR) {
            return 0;
        }
        float span = (float) (RSSI_NEAR - RSSI_FAR);
        return Math.round(100f * (rssiDbm - RSSI_FAR) / span);
    }

    @NonNull
    public static String proximityLabel(int percent) {
        if (percent >= 80) {
            return "Muy cerca";
        }
        if (percent >= 55) {
            return "Cerca";
        }
        if (percent >= 30) {
            return "Media";
        }
        if (percent >= 10) {
            return "Lejos";
        }
        return "Muy lejos / sin señal";
    }

    /** Valor numérico para mostrar (dBm), o null si no hay dato. */
    @NonNull
    public static String formatDbmDisplay(@NonNull String rssiRaw) {
        if (rssiRaw.trim().isEmpty()) {
            return "—";
        }
        int db = parseRssiDbm(rssiRaw);
        return db + " dBm";
    }
}
