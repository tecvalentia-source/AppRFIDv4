package com.confa.apprfid;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.rscja.deviceapi.RFIDWithUHFUART;

/**
 * Potencia y región de frecuencia persistidas; se aplican tras {@link RFIDWithUHFUART#init(android.content.Context)}.
 */
public final class ReaderPrefs {

    private static final String PREFS = "uhf_reader_prefs";
    public static final String KEY_POWER = "power_dbm";
    public static final String KEY_FREQUENCY_MODE = "frequency_mode";

    public static final int POWER_MIN = 0;
    public static final int POWER_MAX = 30;
    public static final int POWER_DEFAULT = 26;

    private ReaderPrefs() {
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getPower(@NonNull Context ctx) {
        return prefs(ctx).getInt(KEY_POWER, POWER_DEFAULT);
    }

    public static int getFrequencyMode(@NonNull Context ctx) {
        return prefs(ctx).getInt(KEY_FREQUENCY_MODE, 0);
    }

    public static void save(@NonNull Context ctx, int powerDbm, int frequencyMode) {
        int p = Math.max(POWER_MIN, Math.min(POWER_MAX, powerDbm));
        prefs(ctx).edit()
                .putInt(KEY_POWER, p)
                .putInt(KEY_FREQUENCY_MODE, Math.max(0, frequencyMode))
                .apply();
    }

    /**
     * Aplica potencia y modo de frecuencia al módulo (no llama a {@code init}).
     */
    public static void applyToReader(@NonNull Context ctx, @NonNull RFIDWithUHFUART reader) {
        try {
            reader.setPower(getPower(ctx));
        } catch (Exception ignored) {
        }
        try {
            reader.setFrequencyMode(getFrequencyMode(ctx));
        } catch (Exception ignored) {
        }
    }
}
