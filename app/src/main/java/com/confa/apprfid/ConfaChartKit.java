package com.confa.apprfid;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estilo Confa y datos para reportes MPAndroidChart.
 */
public final class ConfaChartKit {

    public static final int EXPORT_CHART_SIZE_PX = 900;

    private ConfaChartKit() {
    }

    private static int[] confaPalette(@NonNull Context ctx) {
        return new int[]{
                ContextCompat.getColor(ctx, R.color.chart_confa_blue),
                ContextCompat.getColor(ctx, R.color.chart_confa_yellow),
                ContextCompat.getColor(ctx, R.color.chart_confa_green),
                ContextCompat.getColor(ctx, R.color.chart_confa_blue_dark),
                ContextCompat.getColor(ctx, R.color.chart_confa_yellow_dark),
                ContextCompat.getColor(ctx, R.color.chart_confa_green_dark),
        };
    }

    public static void stylePieForDialog(@NonNull PieChart chart) {
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(42f);
        chart.setTransparentCircleRadius(48f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setTransparentCircleColor(Color.WHITE);
        chart.setDrawCenterText(false);
        chart.setRotationEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setExtraOffsets(12f, 12f, 12f, 12f);
        chart.setEntryLabelColor(Color.DKGRAY);
        chart.setEntryLabelTextSize(11f);
        Legend leg = chart.getLegend();
        leg.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        leg.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        leg.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        leg.setWordWrapEnabled(true);
        leg.setTextSize(11f);
        leg.setTextColor(Color.DKGRAY);
        chart.setBackgroundColor(Color.WHITE);
    }

    public static void stylePieForBitmap(@NonNull PieChart chart) {
        stylePieForDialog(chart);
        chart.setRotationEnabled(false);
    }

    public static void styleBarForBitmap(@NonNull BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBarShadow(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setScaleEnabled(false);
        chart.setExtraOffsets(24f, 24f, 24f, 48f);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisLeft().setTextColor(Color.DKGRAY);
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
        x.setTextColor(Color.DKGRAY);
        chart.getLegend().setEnabled(false);
        chart.setBackgroundColor(Color.WHITE);
    }

    public static void bindUbicacionPie(@NonNull PieChart chart, @NonNull Map<String, Integer> countsByUbicacion) {
        Context ctx = chart.getContext();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(countsByUbicacion.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<PieEntry> pieEntries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int[] pal = confaPalette(ctx);
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            pieEntries.add(new PieEntry(e.getValue().floatValue(), e.getKey()));
            colors.add(pal[i % pal.length]);
        }
        if (pieEntries.isEmpty()) {
            pieEntries.add(new PieEntry(1f, ctx.getString(R.string.chart_no_data)));
            colors.add(pal[0]);
        }
        PieDataSet set = new PieDataSet(pieEntries, "");
        set.setSliceSpace(2f);
        set.setSelectionShift(4f);
        set.setColors(colors);
        set.setValueTextSize(11f);
        set.setValueTextColor(Color.DKGRAY);
        set.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        set.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        PieData data = new PieData(set);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);
    }

    public static void bindReconcileResultPie(@NonNull PieChart chart, int exitosos, int sobrantes, int faltantes) {
        Context ctx = chart.getContext();
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(Math.max(0, exitosos), ctx.getString(R.string.chart_label_exitosos)));
        entries.add(new PieEntry(Math.max(0, sobrantes), ctx.getString(R.string.chart_label_sobrantes)));
        entries.add(new PieEntry(Math.max(0, faltantes), ctx.getString(R.string.chart_label_faltantes)));
        PieDataSet set = new PieDataSet(entries, "");
        set.setSliceSpace(2f);
        set.setColors(
                ContextCompat.getColor(ctx, R.color.chart_confa_green),
                ContextCompat.getColor(ctx, R.color.chart_confa_yellow),
                ContextCompat.getColor(ctx, R.color.chart_confa_blue));
        set.setValueTextSize(12f);
        set.setValueTextColor(Color.DKGRAY);
        set.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        PieData data = new PieData(set);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);
    }

    public static void bindMissingFoundPie(@NonNull PieChart chart, int localizados, int noLocalizados) {
        Context ctx = chart.getContext();
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(Math.max(0, localizados), ctx.getString(R.string.chart_label_found)));
        entries.add(new PieEntry(Math.max(0, noLocalizados), ctx.getString(R.string.chart_label_not_found)));
        PieDataSet set = new PieDataSet(entries, "");
        set.setSliceSpace(2f);
        set.setColors(
                ContextCompat.getColor(ctx, R.color.chart_confa_green),
                ContextCompat.getColor(ctx, R.color.chart_confa_yellow));
        set.setValueTextSize(12f);
        set.setValueTextColor(Color.DKGRAY);
        set.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        PieData data = new PieData(set);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);
    }

    public static void bindMissingBar(@NonNull BarChart chart, int localizados, int noLocalizados) {
        Context ctx = chart.getContext();
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, Math.max(0, localizados)));
        entries.add(new BarEntry(1f, Math.max(0, noLocalizados)));
        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(
                ContextCompat.getColor(ctx, R.color.chart_confa_green),
                ContextCompat.getColor(ctx, R.color.chart_confa_yellow));
        set.setValueTextColor(Color.DKGRAY);
        set.setValueTextSize(12f);
        BarData data = new BarData(set);
        data.setBarWidth(0.45f);
        chart.setData(data);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(new String[]{
                ctx.getString(R.string.chart_label_found_short),
                ctx.getString(R.string.chart_label_not_found_short)
        }));
    }

    @NonNull
    public static Map<String, Integer> countRecordsByUbicacion(@NonNull List<MasterRecord> records) {
        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        for (MasterRecord r : records) {
            if (r == null) {
                continue;
            }
            String u = r.ubicacion == null || r.ubicacion.trim().isEmpty()
                    ? "Sin ubicación"
                    : r.ubicacion.trim();
            map.put(u, map.getOrDefault(u, 0) + 1);
        }
        return map;
    }

    @NonNull
    public static android.graphics.Bitmap renderPieBitmap(@NonNull Context ctx,
            @NonNull PieChart chartProducer) {
        stylePieForBitmap(chartProducer);
        chartProducer.notifyDataSetChanged();
        chartProducer.invalidate();
        return ChartBitmapHelper.createBitmapFromView(chartProducer, EXPORT_CHART_SIZE_PX, EXPORT_CHART_SIZE_PX);
    }

    @NonNull
    public static android.graphics.Bitmap renderBarBitmap(@NonNull BarChart chartProducer) {
        styleBarForBitmap(chartProducer);
        chartProducer.notifyDataSetChanged();
        chartProducer.invalidate();
        return ChartBitmapHelper.createBitmapFromView(chartProducer, EXPORT_CHART_SIZE_PX, (int) (EXPORT_CHART_SIZE_PX * 0.65f));
    }
}
