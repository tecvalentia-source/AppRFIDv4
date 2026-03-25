package com.confa.apprfid;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.rscja.deviceapi.RFIDWithUHFUART;

/**
 * Potencia y volumen de alertas persistidos; se aplican tras {@link RFIDWithUHFUART#init(android.content.Context)}.
 */
public final class ReaderPrefs {

    private static final String PREFS = "uhf_reader_prefs";
    public static final String KEY_POWER = "power_dbm";
    public static final String KEY_ALERT_VOLUME = "alert_volume";
    public static final String KEY_BEEP_ENABLED = "beep_enabled";

    public static final int POWER_MIN = 0;
    public static final int POWER_MAX = 30;
    public static final int POWER_DEFAULT = 26;

    public static final int ALERT_VOLUME_MIN = 0;
    public static final int ALERT_VOLUME_MAX = 100;
    public static final int ALERT_VOLUME_DEFAULT = 80;

    private ReaderPrefs() {
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getPower(@NonNull Context ctx) {
        return prefs(ctx).getInt(KEY_POWER, POWER_DEFAULT);
    }

    public static int getAlertVolume(@NonNull Context ctx) {
        return prefs(ctx).getInt(KEY_ALERT_VOLUME, ALERT_VOLUME_DEFAULT);
    }

    public static boolean isBeepEnabled(@NonNull Context ctx) {
        return prefs(ctx).getBoolean(KEY_BEEP_ENABLED, true);
    }

    public static void setBeepEnabled(@NonNull Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_BEEP_ENABLED, enabled).apply();
    }

    public static void setAlertVolume(@NonNull Context ctx, int volume0to100) {
        int v = Math.max(ALERT_VOLUME_MIN, Math.min(ALERT_VOLUME_MAX, volume0to100));
        prefs(ctx).edit().putInt(KEY_ALERT_VOLUME, v).apply();
    }

    public static void savePowerAndVolume(@NonNull Context ctx, int powerDbm, int alertVolume) {
        int p = Math.max(POWER_MIN, Math.min(POWER_MAX, powerDbm));
        int v = Math.max(ALERT_VOLUME_MIN, Math.min(ALERT_VOLUME_MAX, alertVolume));
        prefs(ctx).edit()
                .putInt(KEY_POWER, p)
                .putInt(KEY_ALERT_VOLUME, v)
                .apply();
    }

    /**
     * Aplica solo potencia al módulo (no llama a {@code init}).
     */
    public static void applyToReader(@NonNull Context ctx, @NonNull RFIDWithUHFUART reader) {
        try {
            reader.setPower(getPower(ctx));
        } catch (Exception ignored) {
        }
    }
}
