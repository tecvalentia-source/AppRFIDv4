package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SingleScanAdapter extends RecyclerView.Adapter<SingleScanAdapter.VH> {

    private final List<SingleScanEntry> items = new ArrayList<>();

    public void append(@NonNull SingleScanEntry e) {
        items.add(0, e);
        notifyItemInserted(0);
    }

    public void clear() {
        int n = items.size();
        items.clear();
        notifyItemRangeRemoved(0, n);
    }

    public int getTotalCount() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_single_scan_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SingleScanEntry e = items.get(position);
        h.tvEpc.setText(e.epc);
        String rssiShow = e.rssiRaw.trim().isEmpty() ? "—" : RssiUiUtils.formatDbmDisplay(e.rssiRaw);
        h.tvRssi.setText(rssiShow);
        int pct = RssiUiUtils.proximityPercent(e.rssiDbm);
        h.progress.setProgress(pct);
        h.tvProx.setText(h.itemView.getContext().getString(R.string.single_scan_proximity_value,
                RssiUiUtils.proximityLabel(pct), pct));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvEpc;
        final TextView tvRssi;
        final TextView tvProx;
        final ProgressBar progress;

        VH(View v) {
            super(v);
            tvEpc = v.findViewById(R.id.tvRowEpc);
            tvRssi = v.findViewById(R.id.tvRowRssi);
            tvProx = v.findViewById(R.id.tvRowProximity);
            progress = v.findViewById(R.id.progressRowProximity);
        }
    }
}
