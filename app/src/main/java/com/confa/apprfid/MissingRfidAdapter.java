package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lista la importación de faltantes (RFID en orden) y el estado localizado según {@link #foundKeys}.
 */
public class MissingRfidAdapter extends RecyclerView.Adapter<MissingRfidAdapter.ViewHolder> {

    private final Set<String> foundKeys;
    private List<String> items = new ArrayList<>();

    public MissingRfidAdapter(@NonNull Set<String> foundKeys) {
        this.foundKeys = foundKeys;
    }

    public void setItems(@NonNull List<String> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_missing_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String raw = items.get(position);
        holder.tvRfid.setText(raw != null ? raw : "");
        String norm = RfidNormalizer.normalize(raw);
        boolean ok = !norm.isEmpty() && foundKeys.contains(norm);
        holder.tvEstado.setText(ok ? holder.itemView.getContext().getString(R.string.missing_row_found_yes)
                : holder.itemView.getContext().getString(R.string.missing_row_found_no));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRfid;
        final TextView tvEstado;

        ViewHolder(View v) {
            super(v);
            tvRfid = v.findViewById(R.id.tvMissingRfid);
            tvEstado = v.findViewById(R.id.tvMissingEstado);
        }
    }
}
