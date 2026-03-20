package com.confa.apprfid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReconciliationEngine {

    public static class Result {
        public List<MasterRecord> exitosos = new ArrayList<>();
        public List<String> sobrantes = new ArrayList<>(); // RFIDs no en el maestro
        public List<MasterRecord> faltantes = new ArrayList<>(); // En maestro pero no escaneados
    }

    public static Result compute(Map<String, MasterRecord> masterMap, Set<String> scannedNorm, Map<String, String> rawMap, String sede) {
        Result result = new Result();

        // 1. Encontrar Exitosos y Faltantes
        for (MasterRecord record : masterMap.values()) {
            if (scannedNorm.contains(record.rfid)) {
                result.exitosos.add(record);
            } else {
                result.faltantes.add(record);
            }
        }

        // 2. Encontrar Sobrantes (lo que escaneaste que no estaba en el maestro)
        for (String norm : scannedNorm) {
            if (!masterMap.containsKey(norm)) {
                result.sobrantes.add(rawMap.get(norm));
            }
        }
        return result;
    }
}