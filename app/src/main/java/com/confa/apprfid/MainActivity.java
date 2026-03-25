package com.confa.apprfid;

import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Conciliación de inventario RFID (Chanway C72) con maestro CSV/Excel XML, escaneo continuo
 * ({@link RFIDWithUHFUART#startInventoryTag}/{@link RFIDWithUHFUART#stopInventory}) y reportes.
 */
public class MainActivity extends AppCompatActivity {

    private enum AppMode {
        RECONCILE,
        MISSING_SEARCH
    }

    private static final String TAG = "MainActivity";
    private static final int MSG_TAG = 1;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> pickMasterLauncher =
            registerForActivityResult(new OpenDocumentPersistable(), this::onMasterDocumentPicked);
    private final ActivityResultLauncher<String[]> pickMissingLauncher =
            registerForActivityResult(new OpenDocumentPersistable(), this::onMissingDocumentPicked);
    private final ActivityResultLauncher<String[]> requestLocationForExport =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> executeExportDownloadTask());

    private RFIDWithUHFUART mReader;
    private boolean inventoryRunning;

    private EditText etScanName;

    private Spinner spinnerSede;
    private Spinner spinnerMode;
    private Button btnImportMaster;
    private Button btnImportMissing;
    private Button btnNewSession;
    private Button btnStartScan;
    private Button btnPause;
    private Button btnResume;
    private Button btnFinalize;
    private Button btnExportReports;
    private RecyclerView rvMaster;
    private TextView tvScanStatus;
    private TextView tvCounterUnique;
    private TextView tvCounterTotal;
    private TextView tvMissingFound;
    private View overlayLoading;

    private final MasterRecordsAdapter masterAdapter = new MasterRecordsAdapter();

    private AppMode appMode = AppMode.RECONCILE;

    /** Todos los registros importados del maestro. */
    private List<MasterRecord> masterRecordsAll = new ArrayList<>();
    private Map<String, MasterRecord> masterByRfidAll = new HashMap<>();
    /** Subconjunto filtrado por la sede del spinner (misma clave normalizada que el escaneo). */
    private final Map<String, MasterRecord> masterByRfidFiltered = new HashMap<>();

    private List<String> missingOrderRaw = new ArrayList<>();
    private final Set<String> missingTargetKeys = new HashSet<>();
    private final Set<String> foundMissingKeys = new HashSet<>();

    private final Set<String> scannedNormalized = new HashSet<>();
    private final Map<String, String> scannedRawByNorm = new HashMap<>();

    private int totalReadEvents;
    private boolean sessionFinalized;
    /** True tras el primer inicio de escaneo en la sesión (hasta Nueva sesión). */
    private boolean scanSessionStarted;

    private ReconciliationEngine.Result lastReconciliation;
    private List<MissingSearchResultRow> lastMissingReport;

    private final Handler tagHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what != MSG_TAG || !(msg.obj instanceof String)) return;
            String epc = ((String) msg.obj).trim();
            if (epc.isEmpty()) return;

            totalReadEvents++;
            String norm = RfidNormalizer.normalize(epc);
            if (norm.isEmpty()) return;

            scannedRawByNorm.put(norm, epc);
            boolean newUnique = scannedNormalized.add(norm);

            if (appMode == AppMode.MISSING_SEARCH && missingTargetKeys.contains(norm)) {
                foundMissingKeys.add(norm);
            }

            tvCounterTotal.setText(getString(R.string.counter_total, totalReadEvents));
            if (newUnique) {
                tvCounterUnique.setText(getString(R.string.counter_unique, scannedNormalized.size()));
            }
            if (appMode == AppMode.MISSING_SEARCH && !missingTargetKeys.isEmpty()) {
                tvMissingFound.setText(getString(R.string.counter_missing_found,
                        foundMissingKeys.size(), missingTargetKeys.size()));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etScanName = findViewById(R.id.etScanName);

        spinnerSede = findViewById(R.id.spinnerSede);
        spinnerMode = findViewById(R.id.spinnerMode);
        btnImportMaster = findViewById(R.id.btnImportMaster);
        btnImportMissing = findViewById(R.id.btnImportMissing);
        btnNewSession = findViewById(R.id.btnNewSession);
        btnStartScan = findViewById(R.id.btnStartScan);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnFinalize = findViewById(R.id.btnFinalize);
        btnExportReports = findViewById(R.id.btnExportReports);
        rvMaster = findViewById(R.id.rvMaster);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvCounterUnique = findViewById(R.id.tvCounterUnique);
        tvCounterTotal = findViewById(R.id.tvCounterTotal);
        tvMissingFound = findViewById(R.id.tvMissingFound);
        overlayLoading = findViewById(R.id.overlayLoading);

        ArrayAdapter<CharSequence> sedeAdapter = ArrayAdapter.createFromResource(
                this, R.array.sedes_inventario, android.R.layout.simple_spinner_dropdown_item);
        spinnerSede.setAdapter(sedeAdapter);
        spinnerSede.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rebuildFilteredMasterForSelectedSede();
                refreshActionStates();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.mode_reconcile),
                        getString(R.string.mode_missing_search)
                });
        spinnerMode.setAdapter(modeAdapter);
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                appMode = position == 0 ? AppMode.RECONCILE : AppMode.MISSING_SEARCH;
                applyModeToUi();
                refreshActionStates();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        rvMaster.setLayoutManager(new LinearLayoutManager(this));
        rvMaster.setAdapter(masterAdapter);
        rvMaster.setHasFixedSize(true);
        rvMaster.setItemViewCacheSize(40);
        rvMaster.setItemAnimator(null);

        btnImportMaster.setOnClickListener(v -> pickMasterLauncher.launch(new String[]{"*/*"}));
        btnImportMissing.setOnClickListener(v -> pickMissingLauncher.launch(new String[]{"*/*"}));
        btnNewSession.setOnClickListener(v -> confirmNewSession());
        btnStartScan.setOnClickListener(v -> startScanning());
        btnPause.setOnClickListener(v -> pauseScanning());
        btnResume.setOnClickListener(v -> resumeScanning());
        btnFinalize.setOnClickListener(v -> finalizeSession());
        btnExportReports.setOnClickListener(v -> exportReports());

        etScanName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshActionStates();
            }
        });

        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && !mReader.init(this)) {
                Toast.makeText(this, "Error al inicializar el hardware RFID.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "RFID init failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "RFID config", e);
            Toast.makeText(this, "Error de configuración del lector: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        newSession();

        applyModeToUi();
        refreshActionStates();
        updateScanStatusIdle();
    }

    private void applyModeToUi() {
        boolean missing = appMode == AppMode.MISSING_SEARCH;
        btnImportMissing.setVisibility(missing ? View.VISIBLE : View.GONE);
        tvMissingFound.setVisibility(missing ? View.VISIBLE : View.GONE);
        if (!missing) {
            tvMissingFound.setText("");
        }
    }

    private void onMasterDocumentPicked(Uri uri) {
        if (uri == null) return;
        maybeTakePersistableReadPermission(uri);
        setLoadingOverlayVisible(true);
        MasterImportLoader.loadMasterAsync(getApplicationContext(), uri, ioExecutor, mainHandler,
                new MasterImportLoader.MasterCallback() {
                    @Override
                    public void onSuccess(@NonNull MasterTableParser.ParseResult result) {
                        setLoadingOverlayVisible(false);
                        masterRecordsAll = new ArrayList<>(result.records);
                        masterByRfidAll = new HashMap<>(result.byNormalizedRfid);
                        rebuildFilteredMasterForSelectedSede();
                        Toast.makeText(MainActivity.this,
                                getString(R.string.import_ok, masterRecordsAll.size(), result.duplicateCount),
                                Toast.LENGTH_LONG).show();
                        if (!masterRecordsAll.isEmpty() && masterByRfidFiltered.isEmpty()) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.import_ok_filtered_empty,
                                            masterRecordsAll.size(), getSelectedSede()),
                                    Toast.LENGTH_LONG).show();
                        }
                        refreshActionStates();
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                        setLoadingOverlayVisible(false);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onMissingDocumentPicked(Uri uri) {
        if (uri == null) return;
        maybeTakePersistableReadPermission(uri);
        setLoadingOverlayVisible(true);
        MasterImportLoader.loadMissingListAsync(getApplicationContext(), uri, ioExecutor, mainHandler,
                new MasterImportLoader.MissingListCallback() {
                    @Override
                    public void onSuccess(@NonNull List<String> orderedRfidsRaw) {
                        setLoadingOverlayVisible(false);
                        missingOrderRaw = new ArrayList<>(orderedRfidsRaw);
                        missingTargetKeys.clear();
                        for (String raw : missingOrderRaw) {
                            String k = RfidNormalizer.normalize(raw);
                            if (!k.isEmpty()) {
                                missingTargetKeys.add(k);
                            }
                        }
                        Toast.makeText(MainActivity.this,
                                getString(R.string.import_missing_ok, missingOrderRaw.size()),
                                Toast.LENGTH_LONG).show();
                        refreshActionStates();
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                        setLoadingOverlayVisible(false);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoadingOverlayVisible(boolean visible) {
        if (overlayLoading != null) {
            overlayLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void maybeTakePersistableReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.w(TAG, "takePersistableUriPermission", e);
        }
    }

    /**
     * Actualiza el mapa filtrado por sede y el RecyclerView.
     */
    private void rebuildFilteredMasterForSelectedSede() {
        masterByRfidFiltered.clear();
        String sede = getSelectedSede();
        List<MasterRecord> forList = new ArrayList<>();
        if (masterRecordsAll.isEmpty()) {
            masterAdapter.setItems(forList);
            return;
        }
        for (MasterRecord r : masterRecordsAll) {
            if (UbicacionMatcher.matchesSelectedSede(r.ubicacion, sede)) {
                forList.add(r);
            }
        }
        for (Map.Entry<String, MasterRecord> e : masterByRfidAll.entrySet()) {
            if (UbicacionMatcher.matchesSelectedSede(e.getValue().ubicacion, sede)) {
                masterByRfidFiltered.put(e.getKey(), e.getValue());
            }
        }
        masterAdapter.setItems(forList);
    }

    @NonNull
    private String getSelectedSede() {
        Object item = spinnerSede.getSelectedItem();
        return item != null ? item.toString().trim() : "";
    }

    @NonNull
    private String getScanNameTrimmed() {
        if (etScanName == null) {
            return "";
        }
        return etScanName.getText() != null ? etScanName.getText().toString().trim() : "";
    }

    private boolean hasScanName() {
        return !getScanNameTrimmed().isEmpty();
    }

    private void confirmNewSession() {
        if (inventoryRunning) {
            Toast.makeText(this, "Detenga el escaneo antes de iniciar una nueva sesión.", Toast.LENGTH_SHORT).show();
            return;
        }
        newSession();
        Toast.makeText(this, R.string.new_session_cleared, Toast.LENGTH_SHORT).show();
    }

    private void newSession() {
        scannedNormalized.clear();
        scannedRawByNorm.clear();
        foundMissingKeys.clear();
        totalReadEvents = 0;
        sessionFinalized = false;
        scanSessionStarted = false;
        lastReconciliation = null;
        lastMissingReport = null;

        masterRecordsAll = new ArrayList<>();
        masterByRfidAll = new HashMap<>();
        masterByRfidFiltered.clear();
        missingOrderRaw = new ArrayList<>();
        missingTargetKeys.clear();
        masterAdapter.setItems(new ArrayList<>());
        if (etScanName != null) {
            etScanName.setText("");
        }

        tvCounterUnique.setText(getString(R.string.counter_unique, 0));
        tvCounterTotal.setText(getString(R.string.counter_total, 0));
        if (appMode == AppMode.MISSING_SEARCH) {
            tvMissingFound.setText(getString(R.string.counter_missing_found, 0, 0));
        }
        updateScanStatusIdle();
        refreshActionStates();
    }

    private void startScanning() {
        if (mReader == null || inventoryRunning || sessionFinalized) return;

        if (!hasScanName()) {
            Toast.makeText(this, R.string.need_scan_name, Toast.LENGTH_SHORT).show();
            return;
        }
        String sede = getSelectedSede();
        if (sede.isEmpty()) {
            Toast.makeText(this, R.string.need_sede, Toast.LENGTH_SHORT).show();
            return;
        }
        if (appMode == AppMode.RECONCILE) {
            if (masterByRfidAll.isEmpty()) {
                Toast.makeText(this, R.string.need_master, Toast.LENGTH_SHORT).show();
                return;
            }
            if (masterByRfidFiltered.isEmpty()) {
                Toast.makeText(this, R.string.need_master_sede, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (missingTargetKeys.isEmpty()) {
                Toast.makeText(this, R.string.need_missing_list, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        attachInventoryCallback();
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        if (mReader.startInventoryTag(param)) {
            inventoryRunning = true;
            scanSessionStarted = true;
            tvScanStatus.setText(R.string.scan_status_scanning);
            refreshActionStates();
            Log.d(TAG, "startInventoryTag");
        } else {
            mReader.setInventoryCallback(null);
            Toast.makeText(this, "Error al iniciar lectura", Toast.LENGTH_SHORT).show();
        }
    }

    private void attachInventoryCallback() {
        mReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo uhftagInfo) {
                if (uhftagInfo == null) return;
                String epc = uhftagInfo.getEPC();
                if (epc == null || epc.isEmpty()) return;
                Message m = tagHandler.obtainMessage(MSG_TAG, epc);
                tagHandler.sendMessage(m);
            }
        });
    }

    private void pauseScanning() {
        if (mReader == null || !inventoryRunning) return;
        if (mReader.stopInventory()) {
            inventoryRunning = false;
            mReader.setInventoryCallback(null);
            tvScanStatus.setText(R.string.scan_status_paused);
            refreshActionStates();
            Log.d(TAG, "pause stopInventory");
        } else {
            Toast.makeText(this, "Error al pausar", Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeScanning() {
        if (mReader == null || inventoryRunning || sessionFinalized) return;
        if (!hasScanName()) {
            Toast.makeText(this, R.string.need_scan_name, Toast.LENGTH_SHORT).show();
            return;
        }
        String sede = getSelectedSede();
        if (sede.isEmpty()) {
            Toast.makeText(this, R.string.need_sede, Toast.LENGTH_SHORT).show();
            return;
        }
        attachInventoryCallback();
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        if (mReader.startInventoryTag(param)) {
            inventoryRunning = true;
            scanSessionStarted = true;
            tvScanStatus.setText(R.string.scan_status_scanning);
            refreshActionStates();
        } else {
            mReader.setInventoryCallback(null);
            Toast.makeText(this, "Error al continuar lectura", Toast.LENGTH_SHORT).show();
        }
    }

    private void finalizeSession() {
        if (sessionFinalized) return;

        if (inventoryRunning) {
            if (mReader != null) {
                mReader.stopInventory();
                mReader.setInventoryCallback(null);
            }
            inventoryRunning = false;
        }

        String sede = getSelectedSede();
        if (sede.isEmpty()) {
            Toast.makeText(this, R.string.need_sede, Toast.LENGTH_SHORT).show();
            refreshActionStates();
            return;
        }
        if (!hasScanName()) {
            Toast.makeText(this, R.string.need_scan_name, Toast.LENGTH_SHORT).show();
            refreshActionStates();
            return;
        }

        tvScanStatus.setText(R.string.finalize_processing);
        btnFinalize.setEnabled(false);

        if (appMode == AppMode.RECONCILE) {
            final Map<String, MasterRecord> masterSnapshot = new HashMap<>(masterByRfidAll);
            ioExecutor.execute(() -> {
                Set<String> scanCopy = new HashSet<>(scannedNormalized);
                Map<String, String> rawCopy = new HashMap<>(scannedRawByNorm);
                ReconciliationEngine.Result result =
                        ReconciliationEngine.compute(masterSnapshot, scanCopy, rawCopy, sede);
                mainHandler.post(() -> {
                    lastReconciliation = result;
                    sessionFinalized = true;
                    tvScanStatus.setText(R.string.scan_status_done);
                    Toast.makeText(this,
                            "Conciliación: OK " + result.exitosos.size()
                                    + " | Sobrantes " + result.sobrantes.size()
                                    + " | Faltantes " + result.faltantes.size(),
                            Toast.LENGTH_LONG).show();
                    refreshActionStates();
                });
            });
        } else {
            ioExecutor.execute(() -> {
                List<MissingSearchResultRow> rows = new ArrayList<>(missingOrderRaw.size());
                for (String raw : missingOrderRaw) {
                    String k = RfidNormalizer.normalize(raw);
                    boolean ok = !k.isEmpty() && foundMissingKeys.contains(k);
                    rows.add(new MissingSearchResultRow(raw, ok, ok ? sede : ""));
                }
                mainHandler.post(() -> {
                    lastMissingReport = rows;
                    sessionFinalized = true;
                    tvScanStatus.setText(R.string.scan_status_done);
                    Toast.makeText(this, "Búsqueda finalizada: " + rows.size() + " filas", Toast.LENGTH_SHORT).show();
                    refreshActionStates();
                });
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void exportReports() {
        if (!sessionFinalized) return;
        if (!hasScanName()) {
            Toast.makeText(this, R.string.need_scan_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasLocationPermission()) {
            requestLocationForExport.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        executeExportDownloadTask();
    }

    /**
     * Coordenadas GPS: se obtienen una vez al exportar (no por cada EPC), en segundo plano.
     */
    private void executeExportDownloadTask() {
        if (!sessionFinalized) return;
        if (!hasScanName()) {
            Toast.makeText(this, R.string.need_scan_name, Toast.LENGTH_SHORT).show();
            return;
        }

        final AppMode mode = appMode;
        final ReconciliationEngine.Result reconSnapshot = lastReconciliation;
        final List<MissingSearchResultRow> missingSnapshot = lastMissingReport != null
                ? new ArrayList<>(lastMissingReport) : null;
        final String sede = getSelectedSede();
        final String scanSan = ExportFileNamer.sanitizeScanName(getScanNameTrimmed());
        final String prefix = SedePrefix.forSedeDisplayName(sede);
        final String dateYyyyMmDd = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());

        setLoadingOverlayVisible(true);
        final Context appCtx = getApplicationContext();

        ioExecutor.execute(() -> {
            try {
                String coord = ExportLocationHelper.getCoordinatesForExport(appCtx);

                if (mode == AppMode.RECONCILE && reconSnapshot != null) {
                    String nOk = ExportFileNamer.buildFileName(prefix, scanSan, dateYyyyMmDd, "EXI");
                    String nSob = ExportFileNamer.buildFileName(prefix, scanSan, dateYyyyMmDd, "SOB");
                    String nFal = ExportFileNamer.buildFileName(prefix, scanSan, dateYyyyMmDd, "FALT");

                    Uri uOk = PublicDownloadsExport.insertWriteAndPublish(appCtx, nOk,
                            out -> ConciliationReportWriter.writeExitosos(out, reconSnapshot.exitosos, coord));
                    Uri uSob = PublicDownloadsExport.insertWriteAndPublish(appCtx, nSob,
                            out -> ConciliationReportWriter.writeSobrantes(out, reconSnapshot.sobrantes, coord));
                    Uri uFal = PublicDownloadsExport.insertWriteAndPublish(appCtx, nFal,
                            out -> ConciliationReportWriter.writeFaltantes(out, reconSnapshot.faltantes, coord));

                    ArrayList<Uri> uris = new ArrayList<>(3);
                    uris.add(uOk);
                    uris.add(uSob);
                    uris.add(uFal);

                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.export_saved_downloads, PublicDownloadsExport.DOWNLOADS_SUBFOLDER),
                                Toast.LENGTH_LONG).show();
                        shareExcelFilesAsChooser(uris,
                                getString(R.string.export_subject_conciliation),
                                getString(R.string.export_chooser_three_files));
                    });
                } else if (mode == AppMode.MISSING_SEARCH && missingSnapshot != null) {
                    String nBus = ExportFileNamer.buildFileName(prefix, scanSan, dateYyyyMmDd, "BUSQFAL");
                    Uri uri = PublicDownloadsExport.insertWriteAndPublish(appCtx, nBus,
                            out -> ConciliationReportWriter.writeMissingSearchReport(out, missingSnapshot, coord));
                    ArrayList<Uri> one = new ArrayList<>(1);
                    one.add(uri);
                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.export_saved_downloads, PublicDownloadsExport.DOWNLOADS_SUBFOLDER),
                                Toast.LENGTH_LONG).show();
                        shareExcelFilesAsChooser(one,
                                getString(R.string.export_subject_missing),
                                getString(R.string.export_chooser));
                    });
                } else {
                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        Toast.makeText(MainActivity.this, R.string.export_nothing, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "export", e);
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    Toast.makeText(MainActivity.this, exportFailureMessage(e), Toast.LENGTH_LONG).show();
                });
            } catch (RuntimeException e) {
                Log.e(TAG, "export runtime", e);
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    Toast.makeText(MainActivity.this, exportFailureMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @NonNull
    private String exportFailureMessage(@NonNull Throwable e) {
        String base = getString(R.string.export_io_error);
        String detail = e.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = e.getClass().getSimpleName();
        }
        return base + ": " + detail;
    }

    /**
     * Comparte uno o varios .xls con {@code content://} y permisos de lectura temporales.
     * Para varios adjuntos el intent usa MIME comodín (mejor compatibilidad con Gmail/Drive).
     */
    private void shareExcelFilesAsChooser(@NonNull ArrayList<Uri> uris,
            @NonNull String subject, @NonNull String chooserTitle) {
        if (uris.isEmpty()) {
            return;
        }
        int readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;

        if (uris.size() == 1) {
            Uri uri = uris.get(0);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.ms-excel");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            send.setClipData(ClipData.newUri(getContentResolver(), subject, uri));
            send.addFlags(readFlags);
            Intent chooser = Intent.createChooser(send, chooserTitle);
            chooser.addFlags(readFlags);
            try {
                startActivity(chooser);
            } catch (android.content.ActivityNotFoundException ex) {
                Log.w(TAG, "share", ex);
                Toast.makeText(this, R.string.export_io_error, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("*/*");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        ClipData clip = new ClipData(
                subject,
                new String[] {"*/*"},
                new ClipData.Item(uris.get(0)));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        send.setClipData(clip);
        send.addFlags(readFlags);
        Intent chooser = Intent.createChooser(send, chooserTitle);
        chooser.addFlags(readFlags);
        try {
            startActivity(chooser);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.w(TAG, "share multiple", ex);
            Toast.makeText(this, R.string.export_io_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateScanStatusIdle() {
        if (sessionFinalized) {
            tvScanStatus.setText(R.string.scan_status_done);
        } else if (inventoryRunning) {
            tvScanStatus.setText(R.string.scan_status_scanning);
        } else {
            tvScanStatus.setText(R.string.scan_status_idle);
        }
    }

    private void refreshActionStates() {
        boolean masterReady = !masterByRfidAll.isEmpty() && !masterByRfidFiltered.isEmpty();
        boolean scanOk = hasScanName();
        boolean prereq = !getSelectedSede().isEmpty()
                && (appMode == AppMode.RECONCILE ? masterReady : !missingTargetKeys.isEmpty());
        boolean canStart = !inventoryRunning && !sessionFinalized && prereq && !scanSessionStarted && scanOk;

        enableIfChanged(btnStartScan, canStart);
        enableIfChanged(btnPause, inventoryRunning);
        enableIfChanged(btnResume, !inventoryRunning && !sessionFinalized && prereq && scanSessionStarted && scanOk);
        boolean canFinalize = !sessionFinalized
                && scanOk
                && !getSelectedSede().isEmpty()
                && (appMode == AppMode.RECONCILE
                ? masterReady
                : !missingTargetKeys.isEmpty());
        enableIfChanged(btnFinalize, canFinalize);

        boolean canExport = sessionFinalized && scanOk
                && ((appMode == AppMode.RECONCILE && lastReconciliation != null)
                || (appMode == AppMode.MISSING_SEARCH && lastMissingReport != null));
        enableIfChanged(btnExportReports, canExport);

        boolean editingAllowed = !inventoryRunning && !sessionFinalized;
        enableIfChanged(btnImportMaster, editingAllowed);
        enableIfChanged(btnImportMissing, editingAllowed);
        enableIfChanged(spinnerMode, editingAllowed);
        enableIfChanged(spinnerSede, editingAllowed);
        enableIfChanged(etScanName, editingAllowed);

        if (!sessionFinalized) {
            updateScanStatusIdle();
        }
    }

    /**
     * Evita llamar {@link View#setEnabled(boolean)} en cada tecla: en algunos equipos industrial eso
     * reinicia el IME y bloquea borrar o editar el texto.
     */
    private static void enableIfChanged(View v, boolean enabled) {
        if (v != null && v.isEnabled() != enabled) {
            v.setEnabled(enabled);
        }
    }

    @Override
    protected void onDestroy() {
        if (mReader != null) {
            if (inventoryRunning) {
                mReader.stopInventory();
            }
            mReader.setInventoryCallback(null);
            mReader.free();
        }
        tagHandler.removeCallbacksAndMessages(null);
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.onDestroy();
    }

    /** OpenDocument con permiso persistente de lectura cuando el proveedor lo permite (Android 11+). */
    private static final class OpenDocumentPersistable extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            Intent intent = super.createIntent(context, input);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            return intent;
        }
    }
}
