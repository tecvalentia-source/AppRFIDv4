package com.confa.apprfid;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inventario de una sola etiqueta con RSSI y barra de proximidad.
 */
public class SingleScanActivity extends AppCompatActivity {

    private RFIDWithUHFUART reader;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private MaterialCardView cardScan;
    private TextView tvEpc;
    private TextView tvRssi;
    private TextView tvProximityLabel;
    private ProgressBar progressProximity;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_scan);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.single_scan_title);
        }

        cardScan = findViewById(R.id.cardScan);
        tvEpc = findViewById(R.id.tvEpc);
        tvRssi = findViewById(R.id.tvRssi);
        tvProximityLabel = findViewById(R.id.tvProximityLabel);
        progressProximity = findViewById(R.id.progressProximity);

        initReader();

        cardScan.setOnClickListener(v -> runSingleInventory());
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
                    tvEpc.setText(R.string.single_scan_no_tag);
                    tvRssi.setText("—");
                    progressProximity.setProgress(0);
                    tvProximityLabel.setText(R.string.single_scan_proximity);
                    UiDialogs.showOk(this, getString(R.string.single_scan_no_tag));
                    return;
                }
                String epc = tag.getEPC().trim();
                String rssiStr = tag.getRssi() != null ? tag.getRssi() : "";
                tvEpc.setText(epc);
                tvRssi.setText(rssiStr.isEmpty() ? "—" : rssiStr + " dBm");
                int db = RssiUiUtils.parseRssiDbm(rssiStr);
                int pct = RssiUiUtils.proximityPercent(db);
                progressProximity.setProgress(pct);
                tvProximityLabel.setText(getString(R.string.single_scan_proximity_value,
                        RssiUiUtils.proximityLabel(pct), pct));
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
