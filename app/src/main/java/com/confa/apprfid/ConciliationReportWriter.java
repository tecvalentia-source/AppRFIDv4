package com.confa.apprfid;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera archivos con extensión {@code .xls} como tabla HTML (Excel y apps de hojas lo abren sin Apache POI).
 */
public final class ConciliationReportWriter {

    private ConciliationReportWriter() {
    }

    public static void writeExitosos(File file, List<MasterRecord> data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 16 * 1024)) {
            writeExitosos(bos, data);
        }
        if (!file.isFile() || file.length() == 0L) {
            throw new IOException("Archivo de reporte vacío o no creado");
        }
    }

    public static void writeExitosos(OutputStream out, List<MasterRecord> data) throws IOException {
        String[] headers = {"RFID", "Ubicacion", "Responsable", "Sede"};
        List<String[]> rows = new ArrayList<>();
        if (data != null) {
            for (MasterRecord rec : data) {
                if (rec == null) {
                    continue;
                }
                rows.add(new String[]{
                        safe(rec.rfid),
                        safe(rec.ubicacion),
                        safe(rec.responsable),
                        safe(rec.sede)
                });
            }
        }
        writeHtmlSpreadsheet(out, "Exitosos", headers, rows);
    }

    public static void writeSobrantes(File file, List<String> data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 16 * 1024)) {
            writeSobrantes(bos, data);
        }
        if (!file.isFile() || file.length() == 0L) {
            throw new IOException("Archivo de reporte vacío o no creado");
        }
    }

    public static void writeSobrantes(OutputStream out, List<String> data) throws IOException {
        String[] headers = {"RFID"};
        List<String[]> rows = new ArrayList<>();
        if (data != null) {
            for (String rfid : data) {
                rows.add(new String[]{safe(rfid)});
            }
        }
        writeHtmlSpreadsheet(out, "Sobrantes", headers, rows);
    }

    public static void writeFaltantes(File file, List<MasterRecord> data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 16 * 1024)) {
            writeFaltantes(bos, data);
        }
        if (!file.isFile() || file.length() == 0L) {
            throw new IOException("Archivo de reporte vacío o no creado");
        }
    }

    public static void writeFaltantes(OutputStream out, List<MasterRecord> data) throws IOException {
        String[] headers = {"RFID", "Ubicacion", "Responsable", "Sede"};
        List<String[]> rows = new ArrayList<>();
        if (data != null) {
            for (MasterRecord rec : data) {
                if (rec == null) {
                    continue;
                }
                rows.add(new String[]{
                        safe(rec.rfid),
                        safe(rec.ubicacion),
                        safe(rec.responsable),
                        safe(rec.sede)
                });
            }
        }
        writeHtmlSpreadsheet(out, "Faltantes", headers, rows);
    }

    public static void writeMissingSearchReport(File file, List<MissingSearchResultRow> data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 16 * 1024)) {
            writeMissingSearchReport(bos, data);
        }
        if (!file.isFile() || file.length() == 0L) {
            throw new IOException("Archivo de reporte vacío o no creado");
        }
    }

    public static void writeMissingSearchReport(OutputStream out, List<MissingSearchResultRow> data)
            throws IOException {
        String[] headers = {"RFID", "Encontrado", "Sede"};
        List<String[]> rows = new ArrayList<>();
        if (data != null) {
            for (MissingSearchResultRow rowData : data) {
                if (rowData == null) {
                    continue;
                }
                rows.add(new String[]{
                        safe(rowData.rfid),
                        rowData.encontrado ? "Sí" : "No",
                        safe(rowData.sede)
                });
            }
        }
        writeHtmlSpreadsheet(out, "Faltantes", headers, rows);
    }

    private static void writeHtmlSpreadsheet(OutputStream rawOut, String sheetTitle, String[] headers,
            List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder(rows.size() * 64 + 256);
        sb.append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
                .append("xmlns:x=\"urn:schemas-microsoft-com:office:excel\">\n")
                .append("<head><meta charset=\"UTF-8\"/>")
                .append("<title>").append(xmlEscape(sheetTitle)).append("</title></head>\n<body>\n")
                .append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"2\">\n<tr>");
        for (String h : headers) {
            sb.append("<th>").append(xmlEscape(h)).append("</th>");
        }
        sb.append("</tr>\n");
        for (String[] row : rows) {
            sb.append("<tr>");
            if (row != null) {
                for (String cell : row) {
                    sb.append("<td>").append(xmlEscape(cell)).append("</td>");
                }
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n</body></html>");

        byte[] utf8 = sb.toString().getBytes(StandardCharsets.UTF_8);
        final BufferedOutputStream bos;
        final boolean closeInner;
        if (rawOut instanceof BufferedOutputStream) {
            bos = (BufferedOutputStream) rawOut;
            closeInner = false;
        } else {
            bos = new BufferedOutputStream(rawOut, 16 * 1024);
            closeInner = true;
        }
        try {
            bos.write(0xEF);
            bos.write(0xBB);
            bos.write(0xBF);
            bos.write(utf8);
            bos.flush();
        } finally {
            if (closeInner) {
                bos.close();
            }
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String xmlEscape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
