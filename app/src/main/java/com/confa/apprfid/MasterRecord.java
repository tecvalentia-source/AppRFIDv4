package com.confa.apprfid;

public class MasterRecord {
    public String rfid;
    public String ubicacion;
    public String responsable;

    public MasterRecord(String rfid, String ubicacion, String responsable) {
        this.rfid = rfid;
        this.ubicacion = ubicacion;
        this.responsable = responsable;
    }
}