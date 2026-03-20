package com.confa.apprfid;

public class RfidNormalizer {
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase();
    }
}