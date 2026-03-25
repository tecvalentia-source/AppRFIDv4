package com.confa.apprfid;

import androidx.annotation.NonNull;

/** Fila para lista agrupada (EPC + RSSI). */
public final class FilterTagRow implements Comparable<FilterTagRow> {
    @NonNull public final String epc;
    public final int rssiDbm;
    @NonNull public final String rssiRaw;

    public FilterTagRow(@NonNull String epc, int rssiDbm, @NonNull String rssiRaw) {
        this.epc = epc;
        this.rssiDbm = rssiDbm;
        this.rssiRaw = rssiRaw;
    }

    @Override
    public int compareTo(FilterTagRow o) {
        return Integer.compare(o.rssiDbm, this.rssiDbm);
    }
}
