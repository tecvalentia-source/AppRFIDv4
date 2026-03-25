package com.confa.apprfid;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/**
 * Potencia (0–30 dBm) y modo de frecuencia persistidos en {@link ReaderPrefs}.
 */
public class SettingsActivity extends AppCompatActivity {

    private SeekBar seekPower;
    private TextView tvPowerValue;
    private Spinner spinnerRegion;

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
        spinnerRegion = findViewById(R.id.spinnerRegion);
        MaterialCardView cardSave = findViewById(R.id.cardSaveSettings);

        seekPower.setMax(ReaderPrefs.POWER_MAX - ReaderPrefs.POWER_MIN);
        int current = ReaderPrefs.getPower(this);
        seekPower.setProgress(current - ReaderPrefs.POWER_MIN);
        updatePowerLabel(current);

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

        ArrayAdapter<CharSequence> regAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_region_labels, android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(regAdapter);
        int mode = ReaderPrefs.getFrequencyMode(this);
        if (mode >= 0 && mode < regAdapter.getCount()) {
            spinnerRegion.setSelection(mode);
        }

        cardSave.setOnClickListener(v -> saveAndFinish());
    }

    private void updatePowerLabel(int powerDbm) {
        tvPowerValue.setText(getString(R.string.settings_power_value, powerDbm));
    }

    private void saveAndFinish() {
        int power = seekPower.getProgress() + ReaderPrefs.POWER_MIN;
        int regionIndex = spinnerRegion.getSelectedItemPosition();
        ReaderPrefs.save(this, power, regionIndex);
        UiDialogs.showOk(this, getString(R.string.settings_saved), this::finish);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
