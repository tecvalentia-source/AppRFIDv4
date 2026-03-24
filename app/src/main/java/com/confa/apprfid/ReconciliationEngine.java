package com.confa.apprfid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReconciliationEngine {

    public static class Result {
        public List<MasterRecord> exitosos = new ArrayList<>();
        public List<SobranteRow> sobrantes = new ArrayList<>();
        public List<MasterRecord> faltantes = new ArrayList<>();
    }

    /**
     * Conciliación por sede usando la columna {@link MasterRecord#ubicacion} del maestro.
     *
     * @param masterByRfid mapa completo EPC normalizado → registro (una fila por EPC)
     * @param scannedNorm  EPC únicos leídos (normalizados)
     * @param rawMap       EPC normalizado → hex/string mostrado
     * @param sedeSpinner  texto de sede seleccionada en la app (ej. Capitalia)
     */
    public static Result compute(Map<String, MasterRecord> masterByRfid,
            Set<String> scannedNorm,
            Map<String, String> rawMap,
            String sedeSpinner) {
        Result result = new Result();
        String sede = UbicacionMatcher.normalizeToken(sedeSpinner);

        for (Map.Entry<String, MasterRecord> e : masterByRfid.entrySet()) {
            String norm = e.getKey();
            MasterRecord rec = e.getValue();
            if (rec == null || norm.isEmpty()) {
                continue;
            }
            boolean inSede = UbicacionMatcher.matchesSelectedSede(rec.ubicacion, sede);
            boolean read = scannedNorm.contains(norm);

            if (inSede && read) {
                result.exitosos.add(rec);
            } else if (inSede) {
                result.faltantes.add(rec);
            }
        }

        for (String norm : scannedNorm) {
            String raw = rawMap != null ? rawMap.get(norm) : norm;
            if (raw == null || raw.isEmpty()) {
                raw = norm;
            }
            MasterRecord rec = masterByRfid.get(norm);
            if (rec == null) {
                result.sobrantes.add(new SobranteRow(raw, SobranteRow.MOTIVO_NO_EN_MAESTRO, null));
                continue;
            }
            if (!UbicacionMatcher.matchesSelectedSede(rec.ubicacion, sede)) {
                String ubi = UbicacionMatcher.normalizeToken(rec.ubicacion);
                result.sobrantes.add(new SobranteRow(raw, SobranteRow.MOTIVO_OTRA_UBICACION,
                        ubi.isEmpty() ? "(vacío)" : ubi));
            }
        }

        return result;
    }
}
