package com.confa.apprfid;

import androidx.annotation.NonNull;

/** * Una lectura en el historial de escaneo individual.
 * Ahora soporta conteo de repeticiones y actualización de señal.
 */
public final class SingleScanEntry {
    @NonNull
    public final String epc; // El EPC no cambia, sigue siendo final.

    @NonNull
    public String rssiRaw;   // Quitamos 'final' para actualizar a la última lectura.
    public int rssiDbm;      // Quitamos 'final' para actualizar a la última lectura.

    public int count;        // Nuevo campo para el contador de lecturas.

    public SingleScanEntry(@NonNull String epc, @NonNull String rssiRaw, int rssiDbm) {
        this.epc = epc;
        this.rssiRaw = rssiRaw;
        this.rssiDbm = rssiDbm;
        this.count = 1; // Por defecto, la primera vez es 1.
    }

    /**
     * Incrementa el contador y actualiza los valores de señal con la lectura más reciente.
     */
    public void increment(String newRssiRaw, int newRssiDbm) {
        this.count++;
        this.rssiRaw = newRssiRaw;
        this.rssiDbm = newRssiDbm;
    }
}