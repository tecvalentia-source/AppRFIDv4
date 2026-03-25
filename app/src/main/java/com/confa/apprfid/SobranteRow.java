package com.confa.apprfid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Fila de reporte de sobrantes: otra ubicación en maestro o EPC desconocido. */
public class SobranteRow {

    public static final String MOTIVO_OTRA_UBICACION = "OTRA_UBICACION";
    public static final String MOTIVO_NO_EN_MAESTRO = "NO_EN_MAESTRO";

    @NonNull
    public final String rfid;
    @NonNull
    public final String motivo;
    /** Ubicación en archivo si motivo es OTRA_UBICACION; vacío si NO_EN_MAESTRO */
    @Nullable
    public final String ubicacionEnMaestro;

    public SobranteRow(@NonNull String rfid, @NonNull String motivo, @Nullable String ubicacionEnMaestro) {
        this.rfid = rfid;
        this.motivo = motivo;
        this.ubicacionEnMaestro = ubicacionEnMaestro;
    }
}
