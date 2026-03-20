package com.confa.apprfid;

public class MissingSearchResultRow {
    public String rfid;
    public boolean encontrado;
    public String sede;

    public MissingSearchResultRow(String rfid, boolean encontrado, String sede) {
        this.rfid = rfid;
        this.encontrado = encontrado;
        this.sede = sede;
    }
}