package com.confa.apprfid;

import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private MaterialCardView cardImportMaster;
    private MaterialCardView cardImportMissing;
    private MaterialCardView cardStartScan;
    private MaterialCardView cardPause;
    private MaterialCardView cardResume;
    private MaterialCardView cardFinalize;
    private MaterialCardView cardNewSession;
    private MaterialCardView cardExportReports;
    private View rowHeadersMaster;
    private View rowHeadersMissing;
    private RecyclerView rvMaster;
    private TextView tvScanStatus;
    private ImageButton btnMasterUbicacionChart;
    private TextView tvCounterUnique;
    private TextView tvCounterTotal;
    private TextView tvMissingFound;
    private View overlayLoading;

    private final MasterRecordsAdapter masterAdapter = new MasterRecordsAdapter();
    private MissingRfidAdapter missingAdapter;

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
            if (appMode == AppMode.MISSING_SEARCH && rvMaster != null
                    && rvMaster.getAdapter() == missingAdapter && missingAdapter != null) {
                missingAdapter.notifyDataSetChanged();
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
        cardImportMaster = findViewById(R.id.cardImportMaster);
        cardImportMissing = findViewById(R.id.cardImportMissing);
        cardStartScan = findViewById(R.id.cardStartScan);
        cardPause = findViewById(R.id.cardPause);
        cardResume = findViewById(R.id.cardResume);
        cardFinalize = findViewById(R.id.cardFinalize);
        cardNewSession = findViewById(R.id.cardNewSession);
        cardExportReports = findViewById(R.id.cardExportReports);
        rowHeadersMaster = findViewById(R.id.rowHeadersMaster);
        rowHeadersMissing = findViewById(R.id.rowHeadersMissing);
        rvMaster = findViewById(R.id.rvMaster);
        missingAdapter = new MissingRfidAdapter(foundMissingKeys);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        btnMasterUbicacionChart = findViewById(R.id.btnMasterUbicacionChart);
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
                applyListUiForMode();
                refreshActionStates();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        rvMaster.setLayoutManager(new LinearLayoutManager(this));
        rvMaster.setHasFixedSize(true);
        rvMaster.setItemViewCacheSize(40);
        rvMaster.setItemAnimator(null);

        cardImportMaster.setOnClickListener(v -> pickMasterLauncher.launch(new String[]{"*/*"}));
        cardImportMissing.setOnClickListener(v -> pickMissingLauncher.launch(new String[]{"*/*"}));
        cardNewSession.setOnClickListener(v -> confirmNewSession());
        cardStartScan.setOnClickListener(v -> startScanning());
        cardPause.setOnClickListener(v -> pauseScanning());
        cardResume.setOnClickListener(v -> resumeScanning());
        cardFinalize.setOnClickListener(v -> finalizeSession());
        cardExportReports.setOnClickListener(v -> exportReports());
        if (btnMasterUbicacionChart != null) {
            btnMasterUbicacionChart.setOnClickListener(v -> showMasterUbicacionChartDialog());
        }

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
            if (mReader != null && mReader.init(this)) {
                ReaderPrefs.applyToReader(this, mReader);
            } else if (mReader != null) {
                showAlert(getString(R.string.rfid_init_failed));
                Log.e(TAG, "RFID init failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "RFID config", e);
            showAlert(getString(R.string.rfid_config_error, e.getMessage() != null ? e.getMessage() : ""));
        }

        tvCounterUnique.setText(getString(R.string.counter_unique, 0));
        tvCounterTotal.setText(getString(R.string.counter_total, 0));

        applyModeToUi();
        applyListUiForMode();
        refreshActionStates();
        updateScanStatusIdle();
    }

    private void showAlert(@NonNull CharSequence message) {
        showAlert(message, null);
    }

    private void showAlert(@NonNull CharSequence message, @Nullable Runnable onOk) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AppRFID_MaterialAlertDialog)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                    d.dismiss();
                    if (onOk != null) {
                        onOk.run();
                    }
                })
                .show();
    }

    private void showAlert(@StringRes int messageResId) {
        showAlert(getString(messageResId));
    }

    /**
     * Lista maestro filtrado por sede o lista de faltantes importada, según el modo.
     */
    private void applyListUiForMode() {
        if (appMode == AppMode.RECONCILE) {
            rowHeadersMaster.setVisibility(View.VISIBLE);
            rowHeadersMissing.setVisibility(View.GONE);
            if (rvMaster.getAdapter() != masterAdapter) {
                rvMaster.setAdapter(masterAdapter);
            }
            rebuildFilteredMasterForSelectedSede();
        } else {
            rowHeadersMaster.setVisibility(View.GONE);
            rowHeadersMissing.setVisibility(View.VISIBLE);
            if (rvMaster.getAdapter() != missingAdapter) {
                rvMaster.setAdapter(missingAdapter);
            }
            missingAdapter.setItems(missingOrderRaw);
        }
    }

    private void refreshPauseResumeVisibility(boolean canResume) {
        if (cardPause == null || cardResume == null) {
            return;
        }
        if (sessionFinalized) {
            cardPause.setVisibility(View.GONE);
            cardResume.setVisibility(View.GONE);
            return;
        }
        if (inventoryRunning) {
            cardPause.setVisibility(View.VISIBLE);
            cardResume.setVisibility(View.GONE);
            cardPause.setEnabled(true);
        } else if (scanSessionStarted) {
            cardPause.setVisibility(View.GONE);
            cardResume.setVisibility(View.VISIBLE);
            enableIfChanged(cardResume, canResume);
        } else {
            cardPause.setVisibility(View.GONE);
            cardResume.setVisibility(View.GONE);
        }
    }

    private void applyModeToUi() {
        boolean missing = appMode == AppMode.MISSING_SEARCH;
        cardImportMissing.setVisibility(missing ? View.VISIBLE : View.GONE);
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
                        StringBuilder msg = new StringBuilder(getString(R.string.import_ok,
                                masterRecordsAll.size(), result.duplicateCount));
                        if (!masterRecordsAll.isEmpty() && masterByRfidFiltered.isEmpty()) {
                            msg.append("\n\n").append(getString(R.string.import_ok_filtered_empty,
                                    masterRecordsAll.size(), getSelectedSede()));
                        }
                        showAlert(msg);
                        refreshActionStates();
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                        setLoadingOverlayVisible(false);
                        showAlert(message);
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
                        showAlert(getString(R.string.import_missing_ok, missingOrderRaw.size()));
                        applyListUiForMode();
                        refreshActionStates();
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                        setLoadingOverlayVisible(false);
                        showAlert(message);
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
            showAlert(getString(R.string.new_session_need_stop_scan));
            return;
        }
        newSession();
        showAlert(getString(R.string.new_session_cleared));
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
        if (etScanName != null) {
            etScanName.setText("");
        }

        tvCounterUnique.setText(getString(R.string.counter_unique, 0));
        tvCounterTotal.setText(getString(R.string.counter_total, 0));
        if (appMode == AppMode.MISSING_SEARCH) {
            tvMissingFound.setText(getString(R.string.counter_missing_found, 0, 0));
        }
        updateScanStatusIdle();
        applyListUiForMode();
        refreshActionStates();
    }

    private void startScanning() {
        if (mReader == null || inventoryRunning || sessionFinalized) return;

        if (!hasScanName()) {
            showAlert(R.string.need_scan_name);
            return;
        }
        String sede = getSelectedSede();
        if (sede.isEmpty()) {
            showAlert(R.string.need_sede);
            return;
        }
        if (appMode == AppMode.RECONCILE) {
            if (masterByRfidAll.isEmpty()) {
                showAlert(R.string.need_master);
                return;
            }
            if (masterByRfidFiltered.isEmpty()) {
                showAlert(R.string.need_master_sede);
                return;
            }
        } else {
            if (missingTargetKeys.isEmpty()) {
                showAlert(R.string.need_missing_list);
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
            showAlert(getString(R.string.error_start_read));
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
            showAlert(getString(R.string.error_pause_read));
        }
    }

    private void resumeScanning() {
        if (mReader == null || inventoryRunning || sessionFinalized) return;
        if (!hasScanName()) {
            showAlert(R.string.need_scan_name);
            return;
        }
        String sede = getSelectedSede();
        if (sede.isEmpty()) {
            showAlert(R.string.need_sede);
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
            showAlert(getString(R.string.error_resume_read));
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
            showAlert(R.string.need_sede);
            refreshActionStates();
            return;
        }
        if (!hasScanName()) {
            showAlert(R.string.need_scan_name);
            refreshActionStates();
            return;
        }

        tvScanStatus.setText(R.string.finalize_processing);
        if (cardFinalize != null) {
            cardFinalize.setEnabled(false);
        }

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
                    showReconcileFinalizeSummary(result);
                    refreshActionStates();
                });
            });
        } else {
            final int totalFaltantesLista = missingTargetKeys.size();
            ioExecutor.execute(() -> {
                List<MissingSearchResultRow> rows = new ArrayList<>(missingOrderRaw.size());
                for (String raw : missingOrderRaw) {
                    String k = RfidNormalizer.normalize(raw);
                    boolean ok = !k.isEmpty() && foundMissingKeys.contains(k);
                    rows.add(new MissingSearchResultRow(raw, ok, ok ? sede : ""));
                }
                final int localizados = foundMissingKeys.size();
                mainHandler.post(() -> {
                    lastMissingReport = rows;
                    sessionFinalized = true;
                    tvScanStatus.setText(R.string.scan_status_done);
                    showMissingFinalizeSummary(localizados, totalFaltantesLista);
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
            showAlert(R.string.need_scan_name);
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
            showAlert(R.string.need_scan_name);
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

                    PieChart pieExport = new PieChart(appCtx);
                    ConfaChartKit.stylePieForBitmap(pieExport);
                    ConfaChartKit.bindReconcileResultPie(pieExport,
                            reconSnapshot.exitosos.size(),
                            reconSnapshot.sobrantes.size(),
                            reconSnapshot.faltantes.size());
                    Bitmap bmpPie = ConfaChartKit.renderPieBitmap(appCtx, pieExport);
                    String nGraf = ExportFileNamer.buildPngFileName(prefix, scanSan, dateYyyyMmDd, "GRAFCONC");
                    Uri uGraf = PublicDownloadsExport.insertPngAndPublish(appCtx, nGraf, bmpPie);
                    bmpPie.recycle();

                    ArrayList<Uri> uris = new ArrayList<>(4);
                    uris.add(uOk);
                    uris.add(uSob);
                    uris.add(uFal);
                    uris.add(uGraf);

                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        showAlert(getString(R.string.export_saved_downloads,
                                PublicDownloadsExport.DOWNLOADS_SUBFOLDER), () ->
                                shareExcelFilesAsChooser(uris,
                                        getString(R.string.export_subject_conciliation),
                                        getString(R.string.export_chooser_four_files)));
                    });
                } else if (mode == AppMode.MISSING_SEARCH && missingSnapshot != null) {
                    String nBus = ExportFileNamer.buildFileName(prefix, scanSan, dateYyyyMmDd, "BUSQFAL");
                    Uri uri = PublicDownloadsExport.insertWriteAndPublish(appCtx, nBus,
                            out -> ConciliationReportWriter.writeMissingSearchReport(out, missingSnapshot, coord));
                    int foundC = 0;
                    for (MissingSearchResultRow r : missingSnapshot) {
                        if (r != null && r.encontrado) {
                            foundC++;
                        }
                    }
                    int notFoundC = Math.max(0, missingSnapshot.size() - foundC);
                    BarChart bar = new BarChart(appCtx);
                    ConfaChartKit.styleBarForBitmap(bar);
                    ConfaChartKit.bindMissingBar(bar, foundC, notFoundC);
                    Bitmap bmpBar = ConfaChartKit.renderBarBitmap(bar);
                    String nBar = ExportFileNamer.buildPngFileName(prefix, scanSan, dateYyyyMmDd, "GRAFBUSFAL");
                    Uri uBar = PublicDownloadsExport.insertPngAndPublish(appCtx, nBar, bmpBar);
                    bmpBar.recycle();

                    ArrayList<Uri> two = new ArrayList<>(2);
                    two.add(uri);
                    two.add(uBar);
                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        showAlert(getString(R.string.export_saved_downloads,
                                PublicDownloadsExport.DOWNLOADS_SUBFOLDER), () ->
                                shareExcelFilesAsChooser(two,
                                        getString(R.string.export_subject_missing),
                                        getString(R.string.export_chooser_two_with_chart)));
                    });
                } else {
                    mainHandler.post(() -> {
                        setLoadingOverlayVisible(false);
                        showAlert(R.string.export_nothing);
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "export", e);
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    showAlert(exportFailureMessage(e));
                });
            } catch (RuntimeException e) {
                Log.e(TAG, "export runtime", e);
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    showAlert(exportFailureMessage(e));
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

    private void showReconcileFinalizeSummary(@NonNull ReconciliationEngine.Result result) {
        View v = getLayoutInflater().inflate(R.layout.dialog_session_summary, null, false);
        TextView tv = v.findViewById(R.id.tvSessionSummaryMessage);
        PieChart chart = v.findViewById(R.id.chartSessionSummary);
        tv.setText(getString(R.string.session_summary_reconcile,
                result.exitosos.size(), result.sobrantes.size(), result.faltantes.size()));
        ConfaChartKit.stylePieForDialog(chart);
        ConfaChartKit.bindReconcileResultPie(chart,
                result.exitosos.size(), result.sobrantes.size(), result.faltantes.size());
        chart.invalidate();
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AppRFID_MaterialAlertDialog)
                .setTitle(R.string.scan_status_done)
                .setView(v)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> d.dismiss())
                .show();
    }

    private void showMissingFinalizeSummary(int localizados, int totalLista) {
        int noLoc = Math.max(0, totalLista - localizados);
        View v = getLayoutInflater().inflate(R.layout.dialog_session_summary, null, false);
        TextView tv = v.findViewById(R.id.tvSessionSummaryMessage);
        PieChart chart = v.findViewById(R.id.chartSessionSummary);
        tv.setText(getString(R.string.session_summary_missing, localizados, totalLista));
        ConfaChartKit.stylePieForDialog(chart);
        ConfaChartKit.bindMissingFoundPie(chart, localizados, noLoc);
        chart.invalidate();
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AppRFID_MaterialAlertDialog)
                .setTitle(R.string.scan_status_done)
                .setView(v)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> d.dismiss())
                .show();
    }

    private void showMasterUbicacionChartDialog() {
        if (masterRecordsAll.isEmpty()) {
            return;
        }
        final Map<String, Integer> counts =
                new HashMap<>(ConfaChartKit.countRecordsByUbicacion(masterRecordsAll));
        View root = getLayoutInflater().inflate(R.layout.dialog_master_ubicacion_chart, null, false);
        PieChart pie = root.findViewById(R.id.chartUbicacion);
        ConfaChartKit.stylePieForDialog(pie);
        ConfaChartKit.bindUbicacionPie(pie, counts);
        pie.invalidate();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_AppRFID_MaterialAlertDialog);
        builder.setTitle(R.string.chart_distrib_ubicacion_title);
        builder.setView(root);
        builder.setPositiveButton(R.string.dialog_ok, (d, w) -> d.dismiss());
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        root.findViewById(R.id.btnChartDownload).setOnClickListener(v ->
                persistMasterUbicacionChartPng(counts, false));
        root.findViewById(R.id.btnChartShare).setOnClickListener(v ->
                persistMasterUbicacionChartPng(counts, true));
        dialog.show();
    }

    private void persistMasterUbicacionChartPng(@NonNull Map<String, Integer> counts, boolean shareAfter) {
        setLoadingOverlayVisible(true);
        final Context appCtx = getApplicationContext();
        String sede = getSelectedSede();
        final String prefix = SedePrefix.forSedeDisplayName(sede.isEmpty() ? "GEN" : sede);
        final String scanPart = hasScanName()
                ? ExportFileNamer.sanitizeScanName(getScanNameTrimmed())
                : "Distrib";
        final String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());

        ioExecutor.execute(() -> {
            try {
                Map<String, Integer> snap = new HashMap<>(counts);
                PieChart pc = new PieChart(appCtx);
                ConfaChartKit.stylePieForBitmap(pc);
                ConfaChartKit.bindUbicacionPie(pc, snap);
                Bitmap bmp = ConfaChartKit.renderPieBitmap(appCtx, pc);
                String name = ExportFileNamer.buildPngFileName(prefix, scanPart, date, "DISTUBI");
                Uri uri = PublicDownloadsExport.insertPngAndPublish(appCtx, name, bmp);
                bmp.recycle();
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    if (shareAfter) {
                        ShareExportHelper.shareSingleImage(MainActivity.this, uri,
                                getString(R.string.chart_distrib_ubicacion_title),
                                getString(R.string.chart_action_share));
                    } else {
                        UiDialogs.showOk(MainActivity.this, getString(R.string.chart_saved_downloads,
                                PublicDownloadsExport.DOWNLOADS_SUBFOLDER));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "chart png", e);
                mainHandler.post(() -> {
                    setLoadingOverlayVisible(false);
                    showAlert(exportFailureMessage(e));
                });
            }
        });
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
                showAlert(R.string.export_io_error);
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
            showAlert(R.string.export_io_error);
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
        boolean canResume = !inventoryRunning && !sessionFinalized && prereq && scanSessionStarted && scanOk;

        enableIfChanged(cardStartScan, canStart);
        boolean canFinalize = !sessionFinalized
                && scanOk
                && !getSelectedSede().isEmpty()
                && (appMode == AppMode.RECONCILE
                ? masterReady
                : !missingTargetKeys.isEmpty());
        enableIfChanged(cardFinalize, canFinalize);

        boolean canExport = sessionFinalized && scanOk
                && ((appMode == AppMode.RECONCILE && lastReconciliation != null)
                || (appMode == AppMode.MISSING_SEARCH && lastMissingReport != null));
        enableIfChanged(cardExportReports, canExport);

        boolean editingAllowed = !inventoryRunning && !sessionFinalized;
        enableIfChanged(cardImportMaster, editingAllowed && appMode == AppMode.RECONCILE);
        enableIfChanged(cardImportMissing, editingAllowed && appMode == AppMode.MISSING_SEARCH);
        enableIfChanged(spinnerMode, editingAllowed);
        enableIfChanged(spinnerSede, editingAllowed);
        enableIfChanged(etScanName, editingAllowed);

        refreshPauseResumeVisibility(canResume);

        if (btnMasterUbicacionChart != null) {
            btnMasterUbicacionChart.setVisibility(
                    appMode == AppMode.RECONCILE && !masterRecordsAll.isEmpty()
                            ? View.VISIBLE
                            : View.GONE);
        }

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
