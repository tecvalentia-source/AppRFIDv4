package com.confa.apprfid;

import androidx.annotation.Nullable;

/** Fila del maestro. {@code sede} es opcional si el archivo trae columna Sede explícita. */
public class MasterRecord {
    public String rfid;
    public String ubicacion;
    public String responsable;
    @Nullable
    public String sede;

    public MasterRecord(String rfid, String ubicacion, String responsable) {
        this(rfid, ubicacion, responsable, null);
    }

    public MasterRecord(String rfid, String ubicacion, String responsable, @Nullable String sede) {
        this.rfid = rfid;
        this.ubicacion = ubicacion;
        this.responsable = responsable;
        this.sede = sede;
    }
}