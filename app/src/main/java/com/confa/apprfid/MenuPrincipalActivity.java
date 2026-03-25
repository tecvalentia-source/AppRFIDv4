package com.confa.apprfid;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

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
                Toast.makeText(this, R.string.menu_soon, Toast.LENGTH_SHORT).show());

        binding.cardFilterIndividual.setOnClickListener(v ->
                Toast.makeText(this, R.string.menu_soon, Toast.LENGTH_SHORT).show());

        binding.cardBulkRead.setOnClickListener(v ->
                Toast.makeText(this, R.string.menu_soon, Toast.LENGTH_SHORT).show());
    }
}
