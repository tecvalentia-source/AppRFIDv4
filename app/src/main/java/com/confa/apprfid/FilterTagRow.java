package com.confa.apprfid;

import androidx.annotation.NonNull;

/** Fila para lista agrupada (EPC + RSSI + conteo de lecturas). */
public final class FilterTagRow implements Comparable<FilterTagRow> {
    @NonNull public final String epc;
    public final int rssiDbm;
    @NonNull public final String rssiRaw;
    public final int readCount;

    public FilterTagRow(@NonNull String epc, int rssiDbm, @NonNull String rssiRaw, int readCount) {
        this.epc = epc;
        this.rssiDbm = rssiDbm;
        this.rssiRaw = rssiRaw;
        this.readCount = readCount;
    }

    @Override
    public int compareTo(FilterTagRow o) {
        int c = Integer.compare(o.rssiDbm, this.rssiDbm);
        if (c != 0) {
            return c;
        }
        return this.epc.compareToIgnoreCase(o.epc);
    }
}
