package com.confa.apprfid;

import androidx.annotation.NonNull;

/** Fila única por RFID en lectura masiva (RSSI y contador actualizables). */
public final class MassLiveRow {
    @NonNull public final String epc;
    @NonNull public String rssiRaw;
    public int rssiDbm;
    public int readCount;

    public MassLiveRow(@NonNull String epc, @NonNull String rssiRaw, int rssiDbm) {
        this.epc = epc;
        this.rssiRaw = rssiRaw;
        this.rssiDbm = rssiDbm;
        this.readCount = 1;
    }
}
