package com.confa.apprfid;
import java.io.File;
import java.util.List;

public class ConciliationReportWriter {
    public static void writeExitosos(File file, List<MasterRecord> data) { /* Lógica Excel */ }
    public static void writeSobrantes(File file, List<String> data) { /* Lógica Excel */ }
    public static void writeFaltantes(File file, List<MasterRecord> data) { /* Lógica Excel */ }
    public static void writeMissingSearchReport(File file, List<MissingSearchResultRow> data) { /* Lógica Excel */ }
}