package com.confa.apprfid;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/**
 * Potencia (0–30 dBm) y volumen global de alertas (pitidos) persistidos en {@link ReaderPrefs}.
 */
public class SettingsActivity extends AppCompatActivity {

    private SeekBar seekPower;
    private TextView tvPowerValue;
    private SeekBar seekAlertVolume;
    private TextView tvVolumeValue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        seekPower = findViewById(R.id.seekPower);
        tvPowerValue = findViewById(R.id.tvPowerValue);
        seekAlertVolume = findViewById(R.id.seekAlertVolume);
        tvVolumeValue = findViewById(R.id.tvVolumeValue);
        MaterialCardView cardSave = findViewById(R.id.cardSaveSettings);

        seekPower.setMax(ReaderPrefs.POWER_MAX - ReaderPrefs.POWER_MIN);
        int currentPower = ReaderPrefs.getPower(this);
        seekPower.setProgress(currentPower - ReaderPrefs.POWER_MIN);
        updatePowerLabel(currentPower);

        seekPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePowerLabel(progress + ReaderPrefs.POWER_MIN);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekAlertVolume.setMax(ReaderPrefs.ALERT_VOLUME_MAX);
        int vol = ReaderPrefs.getAlertVolume(this);
        seekAlertVolume.setProgress(vol);
        updateVolumeLabel(vol);

        seekAlertVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        cardSave.setOnClickListener(v -> saveAndFinish());
    }

    private void updatePowerLabel(int powerDbm) {
        tvPowerValue.setText(getString(R.string.settings_power_value, powerDbm));
    }

    private void updateVolumeLabel(int volume) {
        tvVolumeValue.setText(getString(R.string.settings_volume_value, volume));
    }

    private void saveAndFinish() {
        int power = seekPower.getProgress() + ReaderPrefs.POWER_MIN;
        int volume = seekAlertVolume.getProgress();
        ReaderPrefs.savePowerAndVolume(this, power, volume);
        UiDialogs.showOk(this, getString(R.string.settings_saved), this::finish);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
