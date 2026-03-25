package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MassReadLiveAdapter extends RecyclerView.Adapter<MassReadLiveAdapter.VH> {

    private static final int MAX_ROWS = 3000;

    private final List<MassLiveRow> items = new ArrayList<>();

    public void prepend(@NonNull MassLiveRow row) {
        items.add(0, row);
        if (items.size() > MAX_ROWS) {
            items.remove(items.size() - 1);
            notifyDataSetChanged();
        } else {
            notifyItemInserted(0);
        }
    }

    public void clear() {
        int n = items.size();
        items.clear();
        notifyItemRangeRemoved(0, n);
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
        h.tvRssi.setText(RssiUiUtils.formatDbmDisplay(r.rssiRaw));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvEpc;
        final TextView tvRssi;

        VH(View v) {
            super(v);
            tvEpc = v.findViewById(R.id.tvMassLiveEpc);
            tvRssi = v.findViewById(R.id.tvMassLiveRssi);
        }
    }
}
