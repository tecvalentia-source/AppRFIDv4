package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MassReadLiveAdapter extends RecyclerView.Adapter<MassReadLiveAdapter.VH> {

    private final List<MassLiveRow> items = new ArrayList<>();
    private final Map<String, Integer> normToIndex = new HashMap<>();

    private static String mapKey(@NonNull String epcDisplay) {
        String norm = RfidNormalizer.normalize(epcDisplay);
        if (!norm.isEmpty()) {
            return norm;
        }
        return epcDisplay.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Si el RFID ya existe: incrementa cantidad y actualiza RSSI. Si no, añade fila.
     * @return índice de la fila afectada (nueva o actualizada).
     */
    public int upsertTag(@NonNull String epcDisplay, @NonNull String rssiRaw, int rssiDbm) {
        String key = mapKey(epcDisplay);
        Integer idxObj = normToIndex.get(key);
        if (idxObj != null) {
            int idx = idxObj;
            MassLiveRow r = items.get(idx);
            r.readCount++;
            r.rssiRaw = rssiRaw;
            r.rssiDbm = rssiDbm;
            notifyItemChanged(idx);
            return idx;
        }
        MassLiveRow row = new MassLiveRow(epcDisplay, rssiRaw, rssiDbm);
        items.add(row);
        int pos = items.size() - 1;
        normToIndex.put(key, pos);
        notifyItemInserted(pos);
        return pos;
    }

    public void clear() {
        items.clear();
        normToIndex.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** EPCs en orden de primera aparición (para exportar). */
    @NonNull
    public List<String> getOrderedEpcsForExport() {
        List<String> out = new ArrayList<>(items.size());
        for (MassLiveRow r : items) {
            out.add(r.epc);
        }
        return out;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mass_read_live_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MassLiveRow r = items.get(position);
        h.tvEpc.setText(r.epc);
        h.tvCount.setText(String.valueOf(r.readCount));
        h.tvRssi.setText(RssiUiUtils.formatDbmDisplay(r.rssiRaw));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvEpc;
        final TextView tvCount;
        final TextView tvRssi;

        VH(View v) {
            super(v);
            tvEpc = v.findViewById(R.id.tvMassLiveEpc);
            tvCount = v.findViewById(R.id.tvMassLiveCount);
            tvRssi = v.findViewById(R.id.tvMassLiveRssi);
        }
    }
}
