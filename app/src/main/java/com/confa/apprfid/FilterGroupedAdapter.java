package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilterGroupedAdapter extends RecyclerView.Adapter<FilterGroupedAdapter.VH> {

    private final List<FilterTagRow> items = new ArrayList<>();

    public void setRows(@NonNull List<FilterTagRow> rows) {
        items.clear();
        items.addAll(rows);
        Collections.sort(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_filter_tag_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FilterTagRow r = items.get(position);
        h.tvEpc.setText(r.epc);
        h.tvRssi.setText(h.itemView.getContext().getString(R.string.filter_row_rssi,
                RssiUiUtils.formatDbmDisplay(r.rssiRaw)));
        h.tvReads.setText(h.itemView.getContext().getString(R.string.filter_row_reads, r.readCount));
        int pct = RssiUiUtils.proximityPercent(r.rssiDbm);
        h.progress.setProgress(pct);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvEpc;
        final TextView tvRssi;
        final TextView tvReads;
        final ProgressBar progress;

        VH(View v) {
            super(v);
            tvEpc = v.findViewById(R.id.tvFilterEpc);
            tvRssi = v.findViewById(R.id.tvFilterRssi);
            tvReads = v.findViewById(R.id.tvFilterReads);
            progress = v.findViewById(R.id.progressFilterRow);
        }
    }
}
