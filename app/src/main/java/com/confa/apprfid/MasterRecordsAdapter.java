package com.confa.apprfid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MasterRecordsAdapter extends RecyclerView.Adapter<MasterRecordsAdapter.ViewHolder> {
    private List<MasterRecord> items = new ArrayList<>();

    public void setItems(List<MasterRecord> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_master_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MasterRecord item = items.get(position);
        holder.tvRfid.setText(item.rfid);
        holder.tvUbicacion.setText(item.ubicacion);
        holder.tvResponsable.setText(item.responsable);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRfid, tvUbicacion, tvResponsable;
        ViewHolder(View v) {
            super(v);
            tvRfid = v.findViewById(R.id.tvItemRfid);
            tvUbicacion = v.findViewById(R.id.tvItemUbicacion);
            tvResponsable = v.findViewById(R.id.tvItemResponsable);
        }
    }
}