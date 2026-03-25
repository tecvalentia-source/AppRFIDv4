package com.confa.apprfid;

import androidx.annotation.NonNull;

/**
 * RSSI en cadena (API Chainway) → entero dBm y representación de proximidad.
 */
public final class RssiUiUtils {

    /** Más cercano (dBm típico). */
    private static final int RSSI_NEAR = -45;
    /** Más lejos (dBm típico). */
    private static final int RSSI_FAR = -90;

    private RssiUiUtils() {
    }

    public static int parseRssiDbm(@NonNull String rssi) {
        if (rssi.isEmpty()) {
            return RSSI_FAR;
        }
        String t = rssi.trim().replace("dBm", "").replace("dB", "").trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            try {
                return (int) Float.parseFloat(t);
            } catch (NumberFormatException e2) {
                return RSSI_FAR;
            }
        }
    }

    /** 0–100 (100 = muy cerca). */
    public static int proximityPercent(int rssiDbm) {
        if (rssiDbm >= RSSI_NEAR) {
            return 100;
        }
        if (rssiDbm <= RSSI_FAR) {
            return 0;
        }
        return (int) (100f * (rssiDbm - RSSI_FAR) / (RSSI_NEAR - RSSI_FAR));
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
}
