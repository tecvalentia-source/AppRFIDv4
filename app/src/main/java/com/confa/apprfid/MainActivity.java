package com.confa.apprfid;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lectura RFID UHF (Chanway C72) con {@link RFIDWithUHFUART}.
 * Continuo: {@link RFIDWithUHFUART#startInventoryTag(InventoryParameter)} + callback.
 * Única: {@link RFIDWithUHFUART#inventorySingleTag(InventoryParameter)} (SDK, hilo de fondo).
 */
public class MainActivity extends AppCompatActivity {

    private enum ReadMode {
        CONTINUOUS,
        SINGLE
    }

    private static final String TAG = "MainActivity";
    private static final int MSG_ADD_EPC = 1;
    private static final long SINGLE_WORKER_JOIN_MS = 2500L;
    private static final int EXCEL_SHEET_NAME_MAX = 31;

    private RFIDWithUHFUART mReader;
    private boolean reading = false;
    private ReadMode readMode = ReadMode.CONTINUOUS;

    private final ExecutorService singleReadExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "UHF-inventorySingleTag");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean singleStopRequested;
    private volatile boolean singleWorkerRunning;

    private Button btnStart;
    private Button btnStop;
    private Button btnExport;
    private EditText etEpcFilter;
    private TextView tvTotal;
    private TextView tvUnique;
    private TextView tvEpcList;

    private final Set<String> epcSet = new LinkedHashSet<>();
    private int totalReads = 0;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_ADD_EPC && msg.obj != null) {
                String epc = (String) msg.obj;
                if (epc.isEmpty()) return;
                if (!passesEpcFilter(epc)) return;

                totalReads++;
                boolean isNew = epcSet.add(epc);
                if (isNew) {
                    refreshEpcList();
                }
                tvTotal.setText(String.valueOf(totalReads));
                tvUnique.setText(String.valueOf(epcSet.size()));
                updateExportButtonState();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnExport = findViewById(R.id.btnExport);
        etEpcFilter = findViewById(R.id.etEpcFilter);
        tvTotal = findViewById(R.id.tvTotal);
        tvUnique = findViewById(R.id.tvUnique);
        tvEpcList = findViewById(R.id.tvEpcList);

        try {
            mReader = RFIDWithUHFUART.getInstance();

            if (mReader != null) {
                if (!mReader.init(this)) {
                    Toast.makeText(this, "Error al inicializar el hardware RFID.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "RFID init failed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error de configuración: " + e.getMessage());
            Toast.makeText(this, "Error de configuración del lector: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        btnStart.setOnClickListener(v -> showReadModeDialog());
        btnStop.setOnClickListener(v -> stopInventory());
        btnExport.setOnClickListener(v -> shareEpcListAsXls());
        updateExportButtonState();
    }

    private void updateExportButtonState() {
        if (btnExport == null) return;
        btnExport.setEnabled(!reading && !epcSet.isEmpty());
    }

    private void showReadModeDialog() {
        if (reading) return;

        CharSequence[] options = new CharSequence[]{
                getString(R.string.read_mode_continuous),
                getString(R.string.read_mode_single)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.read_mode_title)
                .setItems(options, (dialog, which) -> {
                    ReadMode mode = (which == 0) ? ReadMode.CONTINUOUS : ReadMode.SINGLE;
                    startInventory(mode);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private boolean passesEpcFilter(String epc) {
        if (epc == null) return false;
        String filter = etEpcFilter.getText().toString().trim();
        if (filter.isEmpty()) return true;
        return filter.equals(epc.trim());
    }

    private static InventoryParameter buildInventoryParameter() {
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        return param;
    }

    private void startInventory(ReadMode mode) {
        if (mReader == null) return;
        if (reading) return;

        readMode = mode;
        epcSet.clear();
        totalReads = 0;
        tvTotal.setText("0");
        tvUnique.setText("0");
        tvEpcList.setText("");

        etEpcFilter.setEnabled(false);

        updateExportButtonState();

        if (mode == ReadMode.SINGLE) {
            startSingleTagRead();
        } else {
            startContinuousInventory();
        }
    }

    private void startContinuousInventory() {
        mReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo uhftagInfo) {
                if (uhftagInfo == null) return;
                String epc = uhftagInfo.getEPC();
                if (epc != null && !epc.isEmpty()) {
                    Message msg = handler.obtainMessage(MSG_ADD_EPC);
                    msg.obj = epc;
                    handler.sendMessage(msg);
                }
            }
        });

        InventoryParameter param = buildInventoryParameter();

        if (mReader.startInventoryTag(param)) {
            reading = true;
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            Toast.makeText(this, "Lectura iniciada", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "startInventoryTag OK");
        } else {
            mReader.setInventoryCallback(null);
            etEpcFilter.setEnabled(true);
            Toast.makeText(this, "Error al iniciar lectura", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "startInventoryTag failed");
        }
    }

    /**
     * Usa el API del SDK {@code inventorySingleTag}. Con filtro, repite hasta coincidencia o
     * hasta {@link #singleStopRequested}. Detener puede tardar hasta que termine la llamada nativa actual.
     */
    private void startSingleTagRead() {
        final String filterSnapshot = etEpcFilter.getText().toString().trim();
        final InventoryParameter param = buildInventoryParameter();

        singleStopRequested = false;
        reading = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        Toast.makeText(this, "Lectura única (acerca la etiqueta)", Toast.LENGTH_SHORT).show();

        singleReadExecutor.execute(() -> {
            UHFTAGInfo found = null;
            boolean stoppedByUser = false;
            try {
                singleWorkerRunning = true;
                RFIDWithUHFUART reader = mReader;
                while (!singleStopRequested && reader != null) {
                    UHFTAGInfo t = reader.inventorySingleTag(param);
                    if (t == null) continue;
                    String epc = t.getEPC();
                    if (epc == null || epc.isEmpty()) continue;
                    if (filterSnapshot.isEmpty() || filterSnapshot.equals(epc.trim())) {
                        found = t;
                        break;
                    }
                }
                stoppedByUser = singleStopRequested && found == null;
            } catch (Exception e) {
                Log.e(TAG, "inventorySingleTag", e);
            } finally {
                singleWorkerRunning = false;
                final UHFTAGInfo tag = found;
                final boolean userStop = stoppedByUser;
                runOnUiThread(() -> finishSingleReadOnMainThread(tag, userStop));
            }
        });
    }

    private void finishSingleReadOnMainThread(UHFTAGInfo tag, boolean stoppedByUser) {
        if (isFinishing()) return;

        reading = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        etEpcFilter.setEnabled(true);

        if (tag != null) {
            String epc = tag.getEPC();
            if (epc != null && !epc.isEmpty()) {
                totalReads++;
                if (epcSet.add(epc)) {
                    refreshEpcList();
                }
                tvTotal.setText(String.valueOf(totalReads));
                tvUnique.setText(String.valueOf(epcSet.size()));
            }
            Toast.makeText(this, "Lectura única completada", Toast.LENGTH_SHORT).show();
        } else if (stoppedByUser) {
            Toast.makeText(this, "Lectura detenida", Toast.LENGTH_SHORT).show();
        }
        updateExportButtonState();
    }

    private void stopInventory() {
        if (mReader == null || !reading) return;

        if (readMode == ReadMode.SINGLE) {
            singleStopRequested = true;
            return;
        }

        if (mReader.stopInventory()) {
            reading = false;
            mReader.setInventoryCallback(null);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            etEpcFilter.setEnabled(true);
            Toast.makeText(this, "Lectura detenida", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "stopInventory OK");
            updateExportButtonState();
        } else {
            Toast.makeText(this, "Error al detener lectura", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "stopInventory failed");
        }
    }

    private void refreshEpcList() {
        StringBuilder sb = new StringBuilder();
        for (String epc : epcSet) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(epc);
        }
        tvEpcList.setText(sb.length() > 0 ? sb.toString() : "");
    }

    /**
     * Excel abre este contenido como hoja aunque la extensión sea .xls (SpreadsheetML / Excel 2003 XML).
     */
    private void shareEpcListAsXls() {
        if (epcSet.isEmpty()) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("mkdirs failed");
            }

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File out = new File(dir, "EPCs_" + stamp + ".xls");
            writeSpreadsheetMlXls(out, epcSet);

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    out);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.ms-excel");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject));
            send.setClipData(ClipData.newUri(getContentResolver(), getString(R.string.export_subject), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(send, getString(R.string.export_chooser_title));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (IOException e) {
            Log.e(TAG, "shareEpcListAsXls", e);
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeSpreadsheetMlXls(File outFile, Iterable<String> epcs) throws IOException {
        String sheet = getString(R.string.export_sheet_name);
        if (sheet.length() > EXCEL_SHEET_NAME_MAX) {
            sheet = sheet.substring(0, EXCEL_SHEET_NAME_MAX);
        }
        String header = getString(R.string.export_column_epc);

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<?mso-application progid=\"Excel.Sheet\"?>\n");
            w.write("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
            w.write("xmlns:o=\"urn:schemas-microsoft-com:office:office\" ");
            w.write("xmlns:x=\"urn:schemas-microsoft-com:office:excel\" ");
            w.write("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
            w.write("xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n");
            w.write("<Worksheet ss:Name=\"");
            w.write(escapeXmlForSpreadsheet(sheet));
            w.write("\"><Table>\n");
            w.write("<Row><Cell><Data ss:Type=\"String\">");
            w.write(escapeXmlForSpreadsheet(header));
            w.write("</Data></Cell></Row>\n");
            for (String epc : epcs) {
                w.write("<Row><Cell><Data ss:Type=\"String\">");
                w.write(escapeXmlForSpreadsheet(epc != null ? epc.trim() : ""));
                w.write("</Data></Cell></Row>\n");
            }
            w.write("</Table></Worksheet></Workbook>\n");
        }
    }

    private static String escapeXmlForSpreadsheet(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }

    private void waitForSingleWorkerQuietly() {
        long deadline = System.currentTimeMillis() + SINGLE_WORKER_JOIN_MS;
        while (singleWorkerRunning && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (reading) {
            stopInventory();
        }
    }

    @Override
    protected void onDestroy() {
        if (reading) {
            if (readMode == ReadMode.SINGLE) {
                singleStopRequested = true;
            } else {
                stopInventory();
            }
        }
        waitForSingleWorkerQuietly();

        handler.removeCallbacksAndMessages(null);
        singleReadExecutor.shutdown();
        try {
            singleReadExecutor.awaitTermination(SINGLE_WORKER_JOIN_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (mReader != null) {
            mReader.setInventoryCallback(null);
            mReader.free();
        }
        super.onDestroy();
    }
}
