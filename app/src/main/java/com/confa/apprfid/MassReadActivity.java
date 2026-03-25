package com.confa.apprfid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inventario masivo y exportación .xls (RFID, Ubicación, Coordenadas).
 */
public class MassReadActivity extends AppCompatActivity {

    private RFIDWithUHFUART reader;
    private boolean scanning;
    private final ArrayList<String> tagOrder = new ArrayList<>();
    private final Set<String> seenNorm = new HashSet<>();

    private EditText etFileName;
    private Spinner spinnerSede;
    private MaterialCardView cardToggle;
    private TextView tvToggle;
    private TextView tvCount;

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
            runOnUiThread(() -> addTagIfNew(epc.trim()));
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
        cardToggle = findViewById(R.id.cardMassToggleScan);
        tvToggle = findViewById(R.id.tvMassToggle);
        tvCount = findViewById(R.id.tvMassCount);

        ArrayAdapter<CharSequence> sedeAdapter = ArrayAdapter.createFromResource(this,
                R.array.sedes_inventario, android.R.layout.simple_spinner_dropdown_item);
        spinnerSede.setAdapter(sedeAdapter);

        cardToggle.setOnClickListener(v -> toggleScan());
        updateCountLabel();
        initReader();
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
                cardToggle.setEnabled(false);
                return;
            }
            ReaderPrefs.applyToReader(this, reader);
        } catch (Exception e) {
            UiDialogs.showOk(this, getString(R.string.rfid_config_error,
                    e.getMessage() != null ? e.getMessage() : ""));
            cardToggle.setEnabled(false);
        }
    }

    private void addTagIfNew(String epc) {
        String norm = RfidNormalizer.normalize(epc);
        if (norm.isEmpty() || !seenNorm.add(norm)) {
            return;
        }
        tagOrder.add(epc);
        updateCountLabel();
    }

    private void updateCountLabel() {
        tvCount.setText(getString(R.string.mass_read_count, tagOrder.size()));
    }

    private void toggleScan() {
        if (scanning) {
            stopScan();
            onStoppedOfferExport();
            return;
        }
        if (reader == null) {
            return;
        }
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        reader.setInventoryCallback(inventoryCb);
        if (reader.startInventoryTag(param)) {
            scanning = true;
            tvToggle.setText(R.string.mass_read_stop);
            cardToggle.setCardBackgroundColor(ContextCompat.getColor(this, R.color.menu_card_orange));
        } else {
            reader.setInventoryCallback(null);
            UiDialogs.showOk(this, getString(R.string.error_start_read));
        }
    }

    private void stopScan() {
        if (reader != null && scanning) {
            try {
                reader.stopInventory();
            } catch (Exception ignored) {
            }
            reader.setInventoryCallback(null);
        }
        scanning = false;
        tvToggle.setText(R.string.mass_read_start);
        cardToggle.setCardBackgroundColor(ContextCompat.getColor(this, R.color.menu_card_white));
    }

    private void onStoppedOfferExport() {
        if (tagOrder.isEmpty()) {
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
        final String prefix = SedePrefix.forSedeDisplayName(ubicacion);
        final String sanitized = ExportFileNamer.sanitizeScanName(base);
        final String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        final String displayName = ExportFileNamer.buildMassReadFileName(prefix, sanitized, date);
        final List<String> snapshot = new ArrayList<>(tagOrder);
        final String ubi = ubicacion;

        io.execute(() -> {
            try {
                String coord = ExportLocationHelper.getCoordinatesForExport(getApplicationContext());
                PublicDownloadsExport.insertWriteAndPublish(getApplicationContext(), displayName,
                        out -> ConciliationReportWriter.writeMassInventoryRead(out, snapshot, ubi, coord));
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.mass_read_export_ok,
                                PublicDownloadsExport.DOWNLOADS_SUBFOLDER, displayName)));
            } catch (Exception e) {
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.mass_read_export_fail,
                                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopScan();
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
