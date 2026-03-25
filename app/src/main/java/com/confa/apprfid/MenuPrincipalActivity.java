package com.confa.apprfid;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.confa.apprfid.databinding.ActivityMenuPrincipalBinding;

/**
 * Pantalla de inicio: acceso al flujo de escaneo/conciliación ({@link MainActivity}) y entradas reservadas.
 */
public class MenuPrincipalActivity extends AppCompatActivity {

    private ActivityMenuPrincipalBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMenuPrincipalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardScanAssets.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        binding.cardScanIndividual.setOnClickListener(v ->
                startActivity(new Intent(this, SingleScanActivity.class)));

        binding.cardFilterIndividual.setOnClickListener(v ->
                startActivity(new Intent(this, FilterScanActivity.class)));

        binding.cardBulkRead.setOnClickListener(v ->
                startActivity(new Intent(this, MassReadActivity.class)));

        binding.cardSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }
}
