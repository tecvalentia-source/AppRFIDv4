package com.confa.apprfid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inventario masivo: filas únicas por RFID (cantidad + RSSI actual), pausa/reanudación y exportación.
 */
public class MassReadActivity extends AppCompatActivity {

    private RFIDWithUHFUART reader;
    private boolean inventoryRunning;
    private boolean pausedAfterRun;
    private int totalReadEvents;

    private EditText etFileName;
    private Spinner spinnerSede;
    private MaterialCardView cardStartContinue;
    private MaterialCardView cardPause;
    private TextView tvStartContinue;
    private MaterialCardView cardExport;
    private MaterialCardView cardClear;
    private TextView tvCount;
    private RecyclerView rvLive;
    private MassReadLiveAdapter liveAdapter;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> requestLocation =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (hasLocationPermission()) {
                            runExportIfPossible();
                        } else {
                            UiDialogs.showOk(this, getString(R.string.location_required_export));
                        }
                    });

    private final IUHFInventoryCallback inventoryCb = new IUHFInventoryCallback() {
        @Override
        public void callback(UHFTAGInfo uhftagInfo) {
            if (uhftagInfo == null) {
                return;
            }
            String epc = uhftagInfo.getEPC();
            if (epc == null || epc.trim().isEmpty()) {
                return;
            }
            String trimmed = epc.trim();
            String rssiStr = uhftagInfo.getRssi() != null ? uhftagInfo.getRssi() : "";
            int db = RssiUiUtils.parseRssiDbm(rssiStr);
            runOnUiThread(() -> {
                totalReadEvents++;
                int pos = liveAdapter.upsertTag(trimmed, rssiStr, db);
                rvLive.scrollToPosition(pos);
                updateCountLabel();
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mass_read);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.mass_read_title);
        }

        etFileName = findViewById(R.id.etMassFileName);
        spinnerSede = findViewById(R.id.spinnerMassSede);
        cardStartContinue = findViewById(R.id.cardMassStartContinue);
        cardPause = findViewById(R.id.cardMassPause);
        tvStartContinue = findViewById(R.id.tvMassStartContinue);
        cardExport = findViewById(R.id.cardMassExport);
        cardClear = findViewById(R.id.cardMassClear);
        tvCount = findViewById(R.id.tvMassCount);
        rvLive = findViewById(R.id.rvMassLive);

        liveAdapter = new MassReadLiveAdapter();
        rvLive.setLayoutManager(new LinearLayoutManager(this));
        rvLive.setAdapter(liveAdapter);

        ArrayAdapter<CharSequence> sedeAdapter = ArrayAdapter.createFromResource(this,
                R.array.sedes_inventario, android.R.layout.simple_spinner_dropdown_item);
        spinnerSede.setAdapter(sedeAdapter);

        cardStartContinue.setOnClickListener(v -> onStartOrContinue());
        cardPause.setOnClickListener(v -> pauseScanning());
        cardExport.setOnClickListener(v -> UiDialogs.showConfirm(this,
                getString(R.string.mass_read_export_confirm),
                this::beginExportFlow));
        cardClear.setOnClickListener(v -> UiDialogs.showConfirm(this,
                getString(R.string.mass_read_clear_confirm),
                this::performClear));

        updateCountLabel();
        refreshScanButtons();
        initReader();
    }

    private void onStartOrContinue() {
        if (reader == null || inventoryRunning) {
            return;
        }
        reader.setInventoryCallback(inventoryCb);
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        if (reader.startInventoryTag(param)) {
            inventoryRunning = true;
            pausedAfterRun = false;
            refreshScanButtons();
        } else {
            reader.setInventoryCallback(null);
            UiDialogs.showOk(this, getString(R.string.error_start_read));
        }
    }

    private void pauseScanning() {
        if (reader == null || !inventoryRunning) {
            return;
        }
        try {
            reader.stopInventory();
        } catch (Exception ignored) {
        }
        reader.setInventoryCallback(null);
        inventoryRunning = false;
        pausedAfterRun = true;
        refreshScanButtons();
    }

    private void refreshScanButtons() {
        if (inventoryRunning) {
            cardStartContinue.setVisibility(View.GONE);
            cardPause.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) cardPause.getLayoutParams();
            pp.weight = 1f;
            cardPause.setLayoutParams(pp);
        } else {
            cardStartContinue.setVisibility(View.VISIBLE);
            cardPause.setVisibility(View.GONE);
            LinearLayout.LayoutParams ps = (LinearLayout.LayoutParams) cardStartContinue.getLayoutParams();
            ps.weight = 1f;
            cardStartContinue.setLayoutParams(ps);
            tvStartContinue.setText(pausedAfterRun ? R.string.mass_read_resume : R.string.mass_read_start);
        }
    }

    private void beginExportFlow() {
        if (liveAdapter.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.mass_read_empty_export));
            return;
        }
        String base = etFileName.getText() != null ? etFileName.getText().toString().trim() : "";
        if (base.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.mass_read_need_filename));
            return;
        }
        if (!hasLocationPermission()) {
            requestLocation.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        runExportIfPossible();
    }

    private void performClear() {
        if (inventoryRunning && reader != null) {
            try {
                reader.stopInventory();
            } catch (Exception ignored) {
            }
            reader.setInventoryCallback(null);
        }
        inventoryRunning = false;
        pausedAfterRun = false;
        totalReadEvents = 0;
        liveAdapter.clear();
        updateCountLabel();
        refreshScanButtons();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initReader() {
        try {
            reader = RFIDWithUHFUART.getInstance();
            if (reader == null || !reader.init(this)) {
                UiDialogs.showOk(this, getString(R.string.rfid_init_failed));
                cardStartContinue.setEnabled(false);
                cardPause.setEnabled(false);
                return;
            }
            ReaderPrefs.applyToReader(this, reader);
        } catch (Exception e) {
            UiDialogs.showOk(this, getString(R.string.rfid_config_error,
                    e.getMessage() != null ? e.getMessage() : ""));
            cardStartContinue.setEnabled(false);
            cardPause.setEnabled(false);
        }
    }

    private void updateCountLabel() {
        tvCount.setText(getString(R.string.mass_read_count_detailed,
                liveAdapter.getItemCount(), totalReadEvents));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void runExportIfPossible() {
        if (!hasLocationPermission()) {
            return;
        }
        Object sedeItem = spinnerSede.getSelectedItem();
        String ubicacion = sedeItem != null ? sedeItem.toString().trim() : "";
        if (ubicacion.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.need_sede));
            return;
        }
        String base = etFileName.getText() != null ? etFileName.getText().toString().trim() : "";
        if (base.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.mass_read_need_filename));
            return;
        }
        if (liveAdapter.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.mass_read_empty_export));
            return;
        }
        final String prefix = SedePrefix.forSedeDisplayName(ubicacion);
        final String sanitized = ExportFileNamer.sanitizeScanName(base);
        final String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        final String displayName = ExportFileNamer.buildMassReadFileName(prefix, sanitized, date);
        final List<String> snapshot = new ArrayList<>(liveAdapter.getOrderedEpcsForExport());
        final String ubi = ubicacion;

        io.execute(() -> {
            try {
                String coord = ExportLocationHelper.getCoordinatesForExport(getApplicationContext());
                android.net.Uri uri = PublicDownloadsExport.insertWriteAndPublish(getApplicationContext(),
                        displayName,
                        out -> ConciliationReportWriter.writeMassInventoryRead(out, snapshot, ubi, coord));
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.mass_read_export_ok,
                                PublicDownloadsExport.DOWNLOADS_SUBFOLDER, displayName),
                        () -> ShareExportHelper.shareSingleSpreadsheet(this, uri,
                                getString(R.string.mass_read_export_subject),
                                getString(R.string.export_chooser))));
            } catch (Exception e) {
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.mass_read_export_fail,
                                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (inventoryRunning && reader != null) {
            try {
                reader.stopInventory();
            } catch (Exception ignored) {
            }
            reader.setInventoryCallback(null);
        }
        inventoryRunning = false;
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reader != null) {
            try {
                reader.setInventoryCallback(null);
                reader.free();
            } catch (Exception ignored) {
            }
            reader = null;
        }
        super.onDestroy();
    }
}
