package com.confa.apprfid;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inventario de una sola etiqueta por pulsación; historial en lista con RSSI y proximidad.
 */
public class SingleScanActivity extends AppCompatActivity {

    private RFIDWithUHFUART reader;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private MaterialCardView cardScan;
    private MaterialCardView cardClear;
    private TextView tvCounter;
    private RecyclerView rvHistory;
    private SingleScanAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_scan);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.single_scan_title);
        }

        cardScan = findViewById(R.id.cardScan);
        cardClear = findViewById(R.id.cardClear);
        tvCounter = findViewById(R.id.tvSingleCounter);
        rvHistory = findViewById(R.id.rvSingleHistory);

        adapter = new SingleScanAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
        updateCounterLabel();

        initReader();

        cardScan.setOnClickListener(v -> runSingleInventory());
        cardClear.setOnClickListener(v -> UiDialogs.showConfirm(this,
                getString(R.string.single_scan_clear_confirm),
                this::performClear));
    }

    private void performClear() {
        adapter.clear();
        updateCounterLabel();
    }

    private void updateCounterLabel() {
        tvCounter.setText(getString(R.string.single_scan_counter, adapter.getTotalCount()));
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
                cardScan.setEnabled(false);
                return;
            }
            ReaderPrefs.applyToReader(this, reader);
        } catch (Exception e) {
            UiDialogs.showOk(this, getString(R.string.rfid_config_error,
                    e.getMessage() != null ? e.getMessage() : ""));
            cardScan.setEnabled(false);
        }
    }

    private void runSingleInventory() {
        if (reader == null) {
            return;
        }
        cardScan.setEnabled(false);
        io.execute(() -> {
            UHFTAGInfo info = null;
            try {
                InventoryParameter param = new InventoryParameter();
                param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));
                info = reader.inventorySingleTag(param);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    cardScan.setEnabled(true);
                    UiDialogs.showOk(this, getString(R.string.single_scan_error, e.getMessage()));
                });
                return;
            }
            final UHFTAGInfo tag = info;
            runOnUiThread(() -> {
                cardScan.setEnabled(true);
                if (tag == null || tag.getEPC() == null || tag.getEPC().isEmpty()) {
                    UiDialogs.showOk(this, getString(R.string.single_scan_no_tag));
                    return;
                }
                String epc = tag.getEPC().trim();
                String rssiStr = tag.getRssi() != null ? tag.getRssi() : "";
                int db = RssiUiUtils.parseRssiDbm(rssiStr);
                adapter.append(new SingleScanEntry(epc, rssiStr, db));
                rvHistory.scrollToPosition(0);
                updateCounterLabel();
            });
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        try {
            io.awaitTermination(2, TimeUnit.SECONDS);
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
