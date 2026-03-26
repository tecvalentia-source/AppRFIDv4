package com.confa.apprfid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Convierte una {@link View} (incl. {@link com.github.mikephil.charting.charts.Chart}) a {@link Bitmap}
 * para exportar como PNG/JPEG.
 */
public final class ChartBitmapHelper {

    private ChartBitmapHelper() {
    }

    @NonNull
    public static Bitmap createBitmapFromView(@NonNull View view, int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Dimensiones inválidas");
        }
        view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        view.draw(canvas);
        return bmp;
    }
}
