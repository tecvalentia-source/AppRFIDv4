package com.confa.apprfid;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Filtro individual (continuo + beep según RSSI) o agrupado (lista, conteos, exportación).
 */
public class FilterScanActivity extends AppCompatActivity {

    private RFIDWithUHFUART reader;
    private boolean groupedMode;
    private boolean scanning;

    private RadioGroup radioMode;
    private EditText etFilter;
    private MaterialCardView cardToggle;
    private TextView tvToggle;
    private LinearLayout panelIndividual;
    private LinearLayout panelGrouped;
    private TextView tvIndEpc;
    private TextView tvIndRssi;
    private TextView tvIndProximity;
    private ProgressBar progressInd;
    private RecyclerView rvGrouped;
    private TextView tvGroupedStats;
    private MaterialCardView cardExportGrouped;
    private MaterialCardView cardClearGrouped;
    private MaterialCardView cardClearIndividual;
    private Switch switchBeep;
    private SeekBar seekBeepVolume;
    private TextView tvBeepVolumeLabel;
    private FilterGroupedAdapter groupedAdapter;

    private ToneGenerator tone;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long nextBeepAt;
    private final Map<String, FilterTagRow> groupedMap = new HashMap<>();
    private int groupedTotalReads;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final IUHFInventoryCallback inventoryCb = new IUHFInventoryCallback() {
        @Override
        public void callback(UHFTAGInfo uhftagInfo) {
            if (uhftagInfo == null) {
                return;
            }
            mainHandler.post(() -> {
                String needle = etFilter.getText() != null ? etFilter.getText().toString().trim() : "";
                onInventoryTag(uhftagInfo, needle);
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_scan);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.filter_scan_title);
        }

        radioMode = findViewById(R.id.radioFilterMode);
        etFilter = findViewById(R.id.etFilterDigits);
        cardToggle = findViewById(R.id.cardToggleScan);
        tvToggle = findViewById(R.id.tvToggleScan);
        panelIndividual = findViewById(R.id.panelIndividual);
        panelGrouped = findViewById(R.id.panelGrouped);
        tvIndEpc = findViewById(R.id.tvIndEpc);
        tvIndRssi = findViewById(R.id.tvIndRssi);
        tvIndProximity = findViewById(R.id.tvIndProximity);
        progressInd = findViewById(R.id.progressIndProximity);
        rvGrouped = findViewById(R.id.rvFilterGrouped);
        tvGroupedStats = findViewById(R.id.tvGroupedStats);
        cardExportGrouped = findViewById(R.id.cardExportGrouped);
        cardClearGrouped = findViewById(R.id.cardClearGrouped);
        cardClearIndividual = findViewById(R.id.cardClearIndividual);
        switchBeep = findViewById(R.id.switchFilterBeep);
        seekBeepVolume = findViewById(R.id.seekFilterBeepVolume);
        tvBeepVolumeLabel = findViewById(R.id.tvFilterBeepVolumeLabel);

        groupedAdapter = new FilterGroupedAdapter();
        rvGrouped.setLayoutManager(new LinearLayoutManager(this));
        rvGrouped.setAdapter(groupedAdapter);

        switchBeep.setChecked(ReaderPrefs.isBeepEnabled(this));
        seekBeepVolume.setMax(ReaderPrefs.ALERT_VOLUME_MAX);
        seekBeepVolume.setProgress(ReaderPrefs.getAlertVolume(this));
        refreshBeepVolumeLabel();
        switchBeep.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ReaderPrefs.setBeepEnabled(this, isChecked);
            recreateToneGenerator();
        });
        seekBeepVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ReaderPrefs.setAlertVolume(FilterScanActivity.this, progress);
                    refreshBeepVolumeLabel();
                    recreateToneGenerator();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        groupedMode = radioMode.getCheckedRadioButtonId() == R.id.radioGrouped;
        updateModePanels();

        radioMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (scanning) {
                stopScanInternal();
                tvToggle.setText(R.string.filter_start_scan);
                setToggleCardColor(false);
            }
            groupedMode = checkedId == R.id.radioGrouped;
            updateModePanels();
            resetGroupedList();
        });

        cardToggle.setOnClickListener(v -> toggleScan());
        cardExportGrouped.setOnClickListener(v -> UiDialogs.showConfirm(this,
                getString(R.string.filter_export_confirm),
                this::exportGroupedResults));
        cardClearGrouped.setOnClickListener(v -> UiDialogs.showConfirm(this,
                getString(R.string.filter_grouped_clear_confirm),
                this::performGroupedClear));

        cardClearIndividual.setOnClickListener(v -> {
            UiDialogs.showConfirm(this,
                    getString(R.string.filter_individual_clear_confirm),
                    this::performIndividualClear);
        });

        initReader();
    }

    private void refreshBeepVolumeLabel() {
        int v = ReaderPrefs.getAlertVolume(this);
        tvBeepVolumeLabel.setText(getString(R.string.filter_beep_volume_value, v));
    }

    private void recreateToneGenerator() {
        if (tone != null) {
            tone.release();
            tone = null;
        }
        if (!ReaderPrefs.isBeepEnabled(this)) {
            return;
        }
        int vol = ReaderPrefs.getAlertVolume(this);
        if (vol <= 0) {
            return;
        }
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, vol);
        } catch (Exception e) {
            tone = null;
        }
    }

    private void updateModePanels() {
        panelIndividual.setVisibility(groupedMode ? View.GONE : View.VISIBLE);
        panelGrouped.setVisibility(groupedMode ? View.VISIBLE : View.GONE);
        rvGrouped.setVisibility(groupedMode ? View.VISIBLE : View.GONE);
    }

    private void resetGroupedList() {
        groupedMap.clear();
        groupedTotalReads = 0;
        groupedAdapter.setRows(new ArrayList<>());
        updateGroupedStatsLabel();
    }

    private void updateGroupedStatsLabel() {
        tvGroupedStats.setText(getString(R.string.filter_grouped_stats,
                groupedMap.size(), groupedTotalReads));
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

    private void setToggleCardColor(boolean active) {
        int c = ContextCompat.getColor(this, active ? R.color.menu_card_orange : R.color.menu_card_lime);
        cardToggle.setCardBackgroundColor(c);
    }

    private void toggleScan() {
        if (scanning) {
            stopScanInternal();
            tvToggle.setText(R.string.filter_start_scan);
            setToggleCardColor(false);
            return;
        }
        String needle = etFilter.getText() != null ? etFilter.getText().toString().trim() : "";
        if (needle.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.filter_need_digits));
            return;
        }
        if (reader == null) {
            return;
        }
        prepareUiForScanStart();
        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
        reader.setInventoryCallback(inventoryCb);
        if (reader.startInventoryTag(param)) {
            scanning = true;
            tvToggle.setText(R.string.filter_stop_scan);
            setToggleCardColor(true);
        } else {
            reader.setInventoryCallback(null);
            UiDialogs.showOk(this, getString(R.string.error_start_read));
        }
    }

    private void prepareUiForScanStart() {
        if (!groupedMode) {
            tvIndEpc.setText("—");
            tvIndRssi.setText("—");
            tvIndProximity.setText("");
            progressInd.setProgress(0);
            nextBeepAt = 0L;
        } else {
            resetGroupedList();
        }
    }

    private void performGroupedClear() {
        if (scanning) {
            stopScanInternal();
            tvToggle.setText(R.string.filter_start_scan);
            setToggleCardColor(false);
        }
        resetGroupedList();
        etFilter.setText("");
    }

    private void performIndividualClear() {
        if (scanning) {
            stopScanInternal();
            tvToggle.setText(R.string.filter_start_scan);
            setToggleCardColor(false);
        }

        etFilter.setText("");

        tvIndEpc.setText("—");
        tvIndRssi.setText("—");
        tvIndProximity.setText("");
        progressInd.setProgress(0);
        nextBeepAt = 0L;
        }
    private void exportGroupedResults() {
        if (groupedMap.isEmpty()) {
            UiDialogs.showOk(this, getString(R.string.filter_export_empty));
            return;
        }
        List<FilterTagRow> rows = new ArrayList<>(groupedMap.values());
        Collections.sort(rows);
        String name = "FiltroAgrupado_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date())
                + ".xls";
        io.execute(() -> {
            try {
                Uri uri = PublicDownloadsExport.insertWriteAndPublish(getApplicationContext(), name,
                        out -> ConciliationReportWriter.writeFilterGroupedExport(out, rows));
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.filter_export_ok, PublicDownloadsExport.DOWNLOADS_SUBFOLDER, name),
                        () -> ShareExportHelper.shareSingleSpreadsheet(this, uri,
                                getString(R.string.filter_export_subject),
                                getString(R.string.filter_export_chooser))));
            } catch (IOException e) {
                runOnUiThread(() -> UiDialogs.showOk(this,
                        getString(R.string.mass_read_export_fail,
                                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        });
    }

    private void onInventoryTag(UHFTAGInfo info, String needle) {
        if (!scanning || needle.isEmpty()) {
            return;
        }
        String epc = info.getEPC();
        if (epc == null || epc.isEmpty()) {
            return;
        }
        String epcTrim = epc.trim();

        if (groupedMode) {
            String epcU = epcTrim.toUpperCase(Locale.ROOT);
            String needleU = needle.toUpperCase(Locale.ROOT);
            if (!epcU.contains(needleU)) {
                return;
            }
        } else if (!epcTrim.equals(needle)) {
            return;
        }

        String rssiStr = info.getRssi() != null ? info.getRssi() : "";
        int db = RssiUiUtils.parseRssiDbm(rssiStr);

        if (groupedMode) {
            groupedTotalReads++;
            String key = RfidNormalizer.normalize(epc);
            if (key.isEmpty()) {
                return;
            }
            FilterTagRow prev = groupedMap.get(key);
            int count = prev == null ? 1 : prev.readCount + 1;
            groupedMap.put(key, new FilterTagRow(epcTrim, db, rssiStr, count));
            groupedAdapter.setRows(new ArrayList<>(groupedMap.values()));
            updateGroupedStatsLabel();
            return;
        }

        tvIndEpc.setText(epcTrim);
        tvIndRssi.setText(getString(R.string.filter_rssi_numeric, db));
        int pct = RssiUiUtils.proximityPercent(db);
        progressInd.setProgress(pct);
        tvIndProximity.setText(getString(R.string.single_scan_proximity_value,
                RssiUiUtils.proximityLabel(pct), pct));

        if (tone != null && ReaderPrefs.isBeepEnabled(this) && ReaderPrefs.getAlertVolume(this) > 0
                && db > -95) {
            long now = SystemClock.uptimeMillis();
            long minInterval = mapDbToMinInterval(db);
            if (now >= nextBeepAt) {
                nextBeepAt = now + minInterval;
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, mapDbToDuration(db));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int mapDbToMinInterval(int db) {
        if (db >= -50) {
            return 80;
        }
        if (db >= -60) {
            return 150;
        }
        if (db >= -70) {
            return 300;
        }
        if (db >= -80) {
            return 700;
        }
        return 1400;
    }

    private static int mapDbToDuration(int db) {
        if (db >= -55) {
            return 120;
        }
        if (db >= -70) {
            return 90;
        }
        return 55;
    }

    private void stopScanInternal() {
        if (reader != null && scanning) {
            try {
                reader.stopInventory();
            } catch (Exception ignored) {
            }
            reader.setInventoryCallback(null);
        }
        scanning = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        recreateToneGenerator();
    }

    @Override
    protected void onStop() {
        stopScanInternal();
        tvToggle.setText(R.string.filter_start_scan);
        setToggleCardColor(false);
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        try {
            io.awaitTermination(4, TimeUnit.SECONDS);
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
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
