package com.confa.apprfid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.CancellationSignal;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Obtiene coordenadas al exportar: una lectura puntual (no por EPC).
 * Si no hay permiso o señal, devuelve texto vacío o {@code sin ubicacion}.
 */
public final class ExportLocationHelper {

    private static final String TAG = "ExportLocation";
    private static final int TIMEOUT_SEC = 10;

    private ExportLocationHelper() {
    }

    /**
     * @return p.ej. {@code "4.609710,-74.081749"} o cadena vacía / mensaje fijo
     */
    @NonNull
    public static String getCoordinatesForExport(@NonNull Context appContext) {
        Context ctx = appContext.getApplicationContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return "";
        }
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return "sin ubicacion";
        }

        Location bestLast = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (bestLast == null || l.getElapsedRealtimeNanos() > bestLast.getElapsedRealtimeNanos())) {
                    bestLast = l;
                }
            } catch (SecurityException e) {
                Log.w(TAG, "lastKnown", e);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AtomicReference<Location> fresh = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            CancellationSignal cancel = new CancellationSignal();
            try {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, cancel,
                        Executors.newSingleThreadExecutor(),
                        location -> {
                            fresh.set(location);
                            latch.countDown();
                        });
                if (latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    Location loc = fresh.get();
                    if (loc != null && isFinite(loc)) {
                        return format(loc);
                    }
                }
            } catch (SecurityException e) {
                Log.w(TAG, "getCurrentLocation", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cancel.cancel();
            }
        }

        if (bestLast != null && isFinite(bestLast)) {
            return format(bestLast);
        }
        return "sin ubicacion";
    }

    private static boolean isFinite(@NonNull Location l) {
        return !Double.isNaN(l.getLatitude()) && !Double.isNaN(l.getLongitude())
                && Math.abs(l.getLatitude()) <= 90.0 && Math.abs(l.getLongitude()) <= 180.0;
    }

    @NonNull
    private static String format(@NonNull Location l) {
        return String.format(Locale.US, "%.6f,%.6f", l.getLatitude(), l.getLongitude());
    }
}
