package com.confa.apprfid;

import androidx.annotation.NonNull;

public final class MassLiveRow {
    @NonNull public final String epc;
    @NonNull public final String rssiRaw;
    public final int rssiDbm;

    public MassLiveRow(@NonNull String epc, @NonNull String rssiRaw, int rssiDbm) {
        this.epc = epc;
        this.rssiRaw = rssiRaw;
        this.rssiDbm = rssiDbm;
    }
}
