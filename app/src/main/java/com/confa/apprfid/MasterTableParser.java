package com.confa.apprfid;

import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maestro: CSV / TXT (coma, punto y coma o tabulador), Excel .xlsx o Excel 2003 XML (SpreadsheetML).
 * Columnas esperadas: RFID, Ubicación, Responsable; opcional: Sede.
 * <p>
 * Lista de faltantes: mismos CSV/TXT/.xlsx/XML, más archivos {@code .xls} generados por esta app
 * (tabla HTML con columna RFID), p. ej. el reporte BUSQFAL exportado.
 */
public final class MasterTableParser {

    private static final int MAX_IMPORT_BYTES = 50 * 1024 * 1024;

    public static class ParseResult {
        public final List<MasterRecord> records;
        public final Map<String, MasterRecord> byNormalizedRfid;
        public final int duplicateCount;

        public ParseResult(List<MasterRecord> records, Map<String, MasterRecord> byNormalizedRfid, int duplicateCount) {
            this.records = records;
            this.byNormalizedRfid = byNormalizedRfid;
            this.duplicateCount = duplicateCount;
        }
    }

    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private MasterTableParser() {
    }

    @NonNull
    public static ParseResult parseMaster(@NonNull InputStream in, @Nullable String fileHint)
            throws IOException, ParseException {
        BufferedInputStream bin = new BufferedInputStream(in, 128 * 1024);
        bin.mark(8);
        byte[] sig4 = new byte[4];
        int sigN = bin.read(sig4);
        bin.reset();
        if (sigN < 0) {
            throw new ParseException("El archivo está vacío.");
        }
        boolean zip = sigN >= 2 && sig4[0] == 0x50 && sig4[1] == 0x4B;
        if (zip) {
            try {
                byte[] data = readStreamFullyCapped(bin, MAX_IMPORT_BYTES);
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                    return parseFromExcelXmlRows(readXlsxRows(bais), fileHint);
                }
            } catch (ParseException e) {
                throw e;
            } catch (IOException e) {
                throw new ParseException("No se pudo leer el archivo Excel (.xlsx).", e);
            } catch (Exception e) {
                throw new ParseException(
                        "No es un Excel .xlsx válido. Si es otro tipo de ZIP, exporte a CSV o TXT.", e);
            }
        }

        Charset textCharset = StandardCharsets.UTF_8;
        bin.mark(8);
        byte[] bomProbe = new byte[4];
        int bomN = bin.read(bomProbe);
        bin.reset();
        if (bomN >= 3 && bomProbe[0] == (byte) 0xEF && bomProbe[1] == (byte) 0xBB && bomProbe[2] == (byte) 0xBF) {
            bin.skip(3);
        } else if (bomN >= 2 && bomProbe[0] == (byte) 0xFF && bomProbe[1] == (byte) 0xFE) {
            textCharset = StandardCharsets.UTF_16LE;
            bin.skip(2);
        } else if (bomN >= 2 && bomProbe[0] == (byte) 0xFE && bomProbe[1] == (byte) 0xFF) {
            textCharset = StandardCharsets.UTF_16BE;
            bin.skip(2);
        }

        BufferedReader probe = new BufferedReader(new InputStreamReader(bin, textCharset), 64 * 1024);
        probe.mark(256 * 1024);
        char[] buf = new char[4096];
        int n = probe.read(buf);
        probe.reset();
        if (n <= 0) {
            throw new ParseException("El archivo está vacío.");
        }
        String head = stripUtf8Bom(new String(buf, 0, Math.min(n, buf.length)).trim());
        boolean looksXml = head.startsWith("<?xml")
                || head.startsWith("<")
                || containsIgnoreCase(head, "<Workbook")
                || containsIgnoreCase(head, "<ss:Workbook");
        if (looksXml) {
            return parseFromExcelXmlRows(readExcelXmlRows(probe), fileHint);
        }
        return parseCsvMaster(probe, fileHint);
    }

    private static final Pattern HTML_TR = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern HTML_TD_TH = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");

    @NonNull
    public static List<String> parseMissingList(@NonNull InputStream in, @Nullable String fileHint)
            throws IOException, ParseException {
        BufferedInputStream bin = new BufferedInputStream(in, 128 * 1024);
        byte[] data = readStreamFullyCapped(bin, MAX_IMPORT_BYTES);
        if (data.length == 0) {
            throw new ParseException("El archivo está vacío.");
        }
        if (data.length >= 2 && data[0] == 0x50 && data[1] == 0x4B) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                return parseMissingFromExcelXml(readXlsxRows(bais));
            } catch (ParseException e) {
                throw e;
            } catch (IOException e) {
                throw new ParseException("No se pudo leer el archivo Excel (.xlsx).", e);
            } catch (Exception e) {
                throw new ParseException(
                        "No es un Excel .xlsx válido para la lista de faltantes.", e);
            }
        }

        String text = stripUtf8Bom(new String(data, StandardCharsets.UTF_8));
        if (looksLikeHtmlTableExport(text)) {
            return parseMissingFromHtmlTable(text);
        }
        String trim = text.trim();
        if (trim.startsWith("<?xml") || trim.startsWith("<")) {
            try {
                return parseMissingFromExcelXml(
                        readExcelXmlRows(new BufferedReader(new StringReader(text))));
            } catch (ParseException e) {
                // No es SpreadsheetML; continuar como CSV/TXT
            }
        }
        return parseMissingCsv(new BufferedReader(new StringReader(text)));
    }

    /** Exportación de la app: .xls con tabla HTML ({@link ConciliationReportWriter}). */
    private static boolean looksLikeHtmlTableExport(@NonNull String text) {
        String u = text.toLowerCase(Locale.ROOT);
        return u.contains("<html") || (u.contains("<table") && u.contains("<tr"));
    }

    @NonNull
    private static List<String> parseMissingFromHtmlTable(@NonNull String html) throws ParseException {
        Matcher trM = HTML_TR.matcher(html);
        List<String> out = new ArrayList<>();
        boolean first = true;
        while (trM.find()) {
            String rowInner = trM.group(1);
            List<String> rawCells = extractHtmlRowCells(rowInner);
            if (rawCells.isEmpty()) {
                continue;
            }
            String col0 = stripHtmlToText(rawCells.get(0));
            if (col0.isEmpty()) {
                continue;
            }
            if (first && looksLikeMissingHeader(col0)) {
                first = false;
                continue;
            }
            first = false;
            String norm = RfidNormalizer.normalize(col0);
            if (!norm.isEmpty()) {
                out.add(col0.trim());
            }
        }
        if (out.isEmpty()) {
            throw new ParseException("No se encontraron RFID en el archivo .xls/HTML. "
                    + "Use el exportado por la app o una columna RFID.");
        }
        return out;
    }

    @NonNull
    private static List<String> extractHtmlRowCells(@NonNull String trInner) {
        List<String> list = new ArrayList<>();
        Matcher m = HTML_TD_TH.matcher(trInner);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    @NonNull
    private static String stripHtmlToText(@Nullable String htmlCell) {
        if (htmlCell == null) {
            return "";
        }
        String t = htmlCell.replaceAll("(?s)<[^>]+>", " ");
        return decodeBasicHtmlEntities(t).replaceAll("\\s+", " ").trim();
    }

    @NonNull
    private static String decodeBasicHtmlEntities(@NonNull String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    private static byte[] readStreamFullyCapped(InputStream in, int maxBytes) throws IOException, ParseException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (bos.size() + n > maxBytes) {
                throw new ParseException("El archivo supera el tamaño máximo permitido para importar.");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static List<List<String>> readXlsxRows(InputStream in) throws IOException, ParseException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() <= 0) {
                throw new ParseException("El Excel no contiene hojas.");
            }
            Sheet sh = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<List<String>> rows = new ArrayList<>();
            int last = sh.getLastRowNum();
            for (int r = sh.getFirstRowNum(); r <= last; r++) {
                Row row = sh.getRow(r);
                if (row == null) {
                    continue;
                }
                short lastCell = row.getLastCellNum();
                if (lastCell <= 0) {
                    continue;
                }
                List<String> cells = new ArrayList<>(Math.max(lastCell, 4));
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : safeTrim(fmt.formatCellValue(cell)));
                }
                rows.add(cells);
            }
            if (rows.isEmpty()) {
                throw new ParseException("La primera hoja del Excel está vacía.");
            }
            return rows;
        } catch (ParseException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("No se pudo abrir el libro Excel (.xlsx).", e);
        }
    }

    private static List<List<String>> readExcelXmlRows(BufferedReader reader) throws IOException, ParseException {
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(reader);
            return pullSpreadsheetRows(p);
        } catch (XmlPullParserException e) {
            throw new ParseException("No se pudo leer el XML (formato Excel XML inválido).", e);
        }
    }

    /** Coincide con {@code Row}, {@code ss:Row}, etc. (SpreadsheetML con prefijos). */
    private static boolean spreadsheetTagEquals(@Nullable String tagName, String localName) {
        if (tagName == null) {
            return false;
        }
        if (localName.equalsIgnoreCase(tagName)) {
            return true;
        }
        int colon = tagName.indexOf(':');
        if (colon >= 0 && colon < tagName.length() - 1) {
            return localName.equalsIgnoreCase(tagName.substring(colon + 1));
        }
        return false;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static List<List<String>> pullSpreadsheetRows(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = null;
        StringBuilder cellBuf = null;
        boolean inData = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            switch (event) {
                case XmlPullParser.START_TAG:
                    if (spreadsheetTagEquals(name, "Row")) {
                        row = new ArrayList<>();
                    } else if (row != null && spreadsheetTagEquals(name, "Cell")) {
                        cellBuf = new StringBuilder();
                    } else if (row != null && spreadsheetTagEquals(name, "Data")) {
                        inData = true;
                    }
                    break;
                case XmlPullParser.TEXT:
                    if (inData && cellBuf != null) {
                        String t = parser.getText();
                        if (t != null) {
                            cellBuf.append(t);
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (spreadsheetTagEquals(name, "Data")) {
                        inData = false;
                    } else if (spreadsheetTagEquals(name, "Cell") && row != null) {
                        row.add(cellBuf != null ? cellBuf.toString().trim() : "");
                        cellBuf = null;
                    } else if (spreadsheetTagEquals(name, "Row") && row != null) {
                        if (!isRowEmpty(row)) {
                            rows.add(row);
                        }
                        row = null;
                    }
                    break;
                default:
                    break;
            }
            event = parser.next();
        }
        return rows;
    }

    private static boolean isRowEmpty(List<String> row) {
        for (String c : row) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static ParseResult parseFromExcelXmlRows(List<List<String>> rows, @Nullable String fileHint)
            throws ParseException {
        if (rows.isEmpty()) {
            throw new ParseException("No hay filas en el archivo.");
        }
        int[] idx = detectHeaderRow(rows);
        int headerRow = idx[0];
        int colRfid = idx[1];
        int colUbic = idx[2];
        int colResp = idx[3];
        int colSede = idx[4];
        List<MasterRecord> list = new ArrayList<>(Math.max(16, rows.size()));
        Map<String, MasterRecord> map = new HashMap<>(Math.max(32, rows.size() * 2));
        int dup = 0;
        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            String rfid = getCell(cells, colRfid);
            if (rfid.isEmpty()) {
                continue;
            }
            String norm = RfidNormalizer.normalize(rfid);
            if (norm.isEmpty()) {
                continue;
            }
            if (map.containsKey(norm)) {
                dup++;
                continue;
            }
            String ubic = colUbic >= 0 ? getCell(cells, colUbic) : "";
            String resp = colResp >= 0 ? getCell(cells, colResp) : "";
            String sede = colSede >= 0 ? getCell(cells, colSede) : null;
            MasterRecord rec = new MasterRecord(rfid.trim(), ubic, resp, emptyToNull(sede));
            list.add(rec);
            map.put(norm, rec);
        }
        if (list.isEmpty()) {
            throw new ParseException("No se encontraron registros con RFID válido."
                    + (fileHint != null ? " (" + fileHint + ")" : ""));
        }
        return new ParseResult(list, map, dup);
    }

    private static List<String> parseMissingFromExcelXml(List<List<String>> rows) throws ParseException {
        if (rows.isEmpty()) {
            throw new ParseException("No hay filas en el archivo.");
        }
        int colRfid = 0;
        int start = 0;
        List<String> first = rows.get(0);
        if (first != null && !first.isEmpty()) {
            for (int c = 0; c < first.size(); c++) {
                String h = normHeader(getCell(first, c));
                if (h.contains("rfid") || h.contains("epc") || h.contains("tag") || h.contains("codigo")) {
                    colRfid = c;
                    start = 1;
                    break;
                }
            }
            if (start == 0) {
                String h0 = normHeader(getCell(first, 0));
                if (h0.contains("rfid") || h0.contains("epc") || h0.contains("tag") || h0.contains("codigo")) {
                    start = 1;
                }
            }
        }
        List<String> out = new ArrayList<>(rows.size());
        for (int i = start; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            String v = getCell(cells, colRfid);
            if (!v.isEmpty() && RfidNormalizer.normalize(v).length() > 0) {
                out.add(v.trim());
            }
        }
        if (out.isEmpty()) {
            throw new ParseException("No se encontraron RFID en la lista de faltantes.");
        }
        return out;
    }

    private static String getCell(List<String> cells, int col) {
        if (col < 0 || col >= cells.size()) {
            return "";
        }
        String s = cells.get(col);
        return s != null ? s.trim() : "";
    }

    private static int[] detectHeaderRow(List<List<String>> rows) throws ParseException {
        for (int r = 0; r < Math.min(3, rows.size()); r++) {
            List<String> cells = rows.get(r);
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            int colRfid = -1;
            int colUbic = -1;
            int colResp = -1;
            int colSede = -1;
            for (int c = 0; c < cells.size(); c++) {
                String h = normHeader(cells.get(c));
                if (h.isEmpty()) {
                    continue;
                }
                if (colRfid < 0 && (h.contains("rfid") || h.contains("epc") || h.contains("tag")
                        || h.contains("codigo") || h.contains("chip"))) {
                    colRfid = c;
                } else if (colUbic < 0 && (h.contains("ubicacion") || h.contains("lugar")
                        || h.contains("location") || h.contains("sitio"))) {
                    colUbic = c;
                } else if (colResp < 0 && (h.contains("responsable") || h.contains("custodio")
                        || h.contains("usuario") || h.equals("resp"))) {
                    colResp = c;
                } else if (colSede < 0 && (h.contains("sede") || h.contains("centro")
                        || h.contains("delegacion") || h.contains("site"))) {
                    colSede = c;
                }
            }
            if (colRfid >= 0) {
                if (colUbic < 0 || colResp < 0) {
                    for (int c = 0; c < cells.size(); c++) {
                        if (c == colRfid) {
                            continue;
                        }
                        if (colSede >= 0 && c == colSede) {
                            continue;
                        }
                        if (colUbic < 0) {
                            colUbic = c;
                        } else if (colResp < 0) {
                            colResp = c;
                            break;
                        }
                    }
                }
                if (colUbic < 0 || colResp < 0) {
                    throw new ParseException("No se pudieron detectar columnas Ubicación y Responsable.");
                }
                return new int[]{r, colRfid, colUbic, colResp, colSede};
            }
        }
        if (rows.get(0).size() >= 3) {
            return new int[]{-1, 0, 1, 2, -1};
        }
        throw new ParseException("No se reconoce la cabecera (RFID / Ubicación / Responsable).");
    }

    private static ParseResult parseCsvMaster(BufferedReader reader, @Nullable String fileHint)
            throws IOException, ParseException {
        String firstLine = stripUtf8Bom(readFirstNonEmptyLine(reader));
        if (firstLine == null) {
            throw new ParseException("El archivo está vacío.");
        }
        char delim = detectDelimiter(firstLine);
        String[] firstCells = splitCsvLine(firstLine, delim);
        boolean hasHeader = looksLikeHeaderRow(firstCells);
        int colRfid;
        int colUbic;
        int colResp;
        int colSede;
        if (hasHeader) {
            colRfid = findColumn(firstCells, "rfid", "epc", "tag", "codigo", "chip");
            colUbic = findColumn(firstCells, "ubicacion", "lugar", "location", "sitio");
            colResp = findColumn(firstCells, "responsable", "custodio", "usuario", "resp");
            colSede = findColumn(firstCells, "sede", "centro", "delegacion", "site");
            if (colRfid < 0) {
                throw new ParseException("Falta la columna RFID en la cabecera.");
            }
            if (colUbic < 0 || colResp < 0) {
                for (int c = 0; c < firstCells.length; c++) {
                    if (c == colRfid) {
                        continue;
                    }
                    if (colSede >= 0 && c == colSede) {
                        continue;
                    }
                    if (colUbic < 0) {
                        colUbic = c;
                    } else if (colResp < 0) {
                        colResp = c;
                        break;
                    }
                }
            }
            if (colUbic < 0 || colResp < 0) {
                throw new ParseException("Faltan columnas Ubicación o Responsable en la cabecera.");
            }
        } else {
            if (firstCells.length < 3) {
                throw new ParseException("Se esperan al menos 3 columnas: RFID, Ubicación, Responsable.");
            }
            colRfid = 0;
            colUbic = 1;
            colResp = 2;
            colSede = firstCells.length > 3 ? 3 : -1;
        }

        List<MasterRecord> list = new ArrayList<>(10_000);
        Map<String, MasterRecord> map = new HashMap<>(16_384);
        int dup = 0;

        if (!hasHeader) {
            dup += addCsvDataRow(firstCells, colRfid, colUbic, colResp, colSede, list, map);
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] cells = splitCsvLine(line, delim);
            dup += addCsvDataRow(cells, colRfid, colUbic, colResp, colSede, list, map);
        }

        if (list.isEmpty()) {
            throw new ParseException("No se encontraron registros con RFID válido."
                    + (fileHint != null ? " (" + fileHint + ")" : ""));
        }
        return new ParseResult(list, map, dup);
    }

    private static String stripUtf8Bom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    @Nullable
    private static String readFirstNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }

    /** @return 1 si la fila es duplicado por RFID, 0 en caso contrario */
    private static int addCsvDataRow(String[] cells, int colRfid, int colUbic, int colResp, int colSede,
            List<MasterRecord> list, Map<String, MasterRecord> map) {
        String rfid = colRfid >= 0 && colRfid < cells.length ? safeTrim(cells[colRfid]) : "";
        if (rfid.isEmpty()) {
            return 0;
        }
        String norm = RfidNormalizer.normalize(rfid);
        if (norm.isEmpty()) {
            return 0;
        }
        if (map.containsKey(norm)) {
            return 1;
        }
        String ubic = colUbic >= 0 && colUbic < cells.length ? safeTrim(cells[colUbic]) : "";
        String resp = colResp >= 0 && colResp < cells.length ? safeTrim(cells[colResp]) : "";
        String sedeVal = colSede >= 0 && colSede < cells.length ? safeTrim(cells[colSede]) : "";
        MasterRecord rec = new MasterRecord(rfid, ubic, resp, emptyToNull(sedeVal));
        list.add(rec);
        map.put(norm, rec);
        return 0;
    }

    private static String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }

    private static String emptyToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }

    private static List<String> parseMissingCsv(BufferedReader reader) throws IOException, ParseException {
        String line;
        List<String> out = new ArrayList<>(1024);
        boolean first = true;
        int col = 0;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            char delim = detectDelimiter(line);
            String[] cells = splitCsvLine(line, delim);
            if (first) {
                if (looksLikeMissingHeader(cells[0])) {
                    first = false;
                    continue;
                }
                first = false;
            }
            String v = col < cells.length ? safeTrim(cells[col]) : "";
            if (!v.isEmpty() && !RfidNormalizer.normalize(v).isEmpty()) {
                out.add(v);
            }
        }
        if (out.isEmpty()) {
            throw new ParseException("No se encontraron RFID en la lista de faltantes.");
        }
        return out;
    }

    private static boolean looksLikeMissingHeader(String cell) {
        String h = normHeader(cell);
        return h.contains("rfid") || h.contains("epc") || h.contains("tag") || h.contains("codigo");
    }

    private static boolean looksLikeHeaderRow(String[] cells) {
        if (cells.length < 2) {
            return false;
        }
        int hits = 0;
        for (String cell : cells) {
            String h = normHeader(cell);
            if (h.contains("rfid") || h.contains("epc") || h.contains("tag") || h.contains("codigo")) {
                hits++;
            }
            if (h.contains("ubicacion") || h.contains("responsable") || h.contains("sede")) {
                hits++;
            }
        }
        return hits >= 2;
    }

    private static int findColumn(String[] header, String... keys) {
        for (int i = 0; i < header.length; i++) {
            String h = normHeader(header[i]);
            for (String k : keys) {
                if (h.contains(k)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String normHeader(String h) {
        if (h == null) {
            return "";
        }
        String x = h.trim().toLowerCase(Locale.ROOT);
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return x;
    }

    private static char detectDelimiter(String line) {
        int tab = countChar(line, '\t');
        int semi = countChar(line, ';');
        int comma = countChar(line, ',');
        if (tab > 0 && tab >= semi && tab >= comma) {
            return '\t';
        }
        if (semi > comma) {
            return ';';
        }
        return ',';
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * Separador CSV con soporte de comillas dobles.
     */
    @NonNull
    static String[] splitCsvLine(@NonNull String line, char delimiter) {
        List<String> out = new ArrayList<>(16);
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == delimiter) {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
