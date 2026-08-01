package com.bykerimoff.player.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bykerimoff.player.R;
import com.bykerimoff.player.models.EpgProgram;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.ViewHolder> {

    private final List<EpgProgram> programs;
    private final OnProgramClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());

    public interface OnProgramClickListener {
        void onProgramClick(EpgProgram program);
        void onProgramLongClick(EpgProgram program);
    }

    public ArchiveAdapter(List<EpgProgram> programs, OnProgramClickListener listener) {
        this.programs = programs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_archive, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgProgram program = programs.get(position);
        holder.tvTitle.setText(program.getTitle());
        
        String timeStr = dateFormat.format(new Date(program.getStartTime())) + " " +
                        timeFormat.format(new Date(program.getStartTime())) + " - " +
                        timeFormat.format(new Date(program.getEndTime()));
        holder.tvTime.setText(timeStr);

        holder.itemView.setOnClickListener(v -> listener.onProgramClick(program));
        
        holder.itemView.setOnLongClickListener(v -> {
            listener.onProgramLongClick(program);
            return true;
        });
        
        // Fokus effekti
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setScaleX(1.05f);
                v.setScaleY(1.05f);
                holder.tvTitle.setTextColor(v.getContext().getResources().getColor(R.color.gold_primary));
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                holder.tvTitle.setTextColor(v.getContext().getResources().getColor(R.color.white));
            }
        });
    }

    @Override
    public int getItemCount() {
        return programs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime;
        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvProgramTitle);
            tvTime = itemView.findViewById(R.id.tvProgramTime);
        }
    }
}
