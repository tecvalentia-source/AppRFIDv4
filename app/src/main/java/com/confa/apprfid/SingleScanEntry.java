package com.confa.apprfid;

import androidx.annotation.NonNull;

/** Una lectura en el historial de escaneo individual. */
public final class SingleScanEntry {
    @NonNull public final String epc;
    @NonNull public final String rssiRaw;
    public final int rssiDbm;

    public SingleScanEntry(@NonNull String epc, @NonNull String rssiRaw, int rssiDbm) {
        this.epc = epc;
        this.rssiRaw = rssiRaw;
        this.rssiDbm = rssiDbm;
    }
}
