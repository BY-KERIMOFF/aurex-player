package com.bykerimoff.player.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bykerimoff.player.R;
import com.bykerimoff.player.models.EpgProgram;
import com.bykerimoff.player.utils.ThemeManager;

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

        int themeColor = ThemeManager.INSTANCE.getThemeColor(holder.itemView.getContext());

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
                v.setBackgroundColor(themeColor);
                holder.tvTitle.setTextColor(Color.BLACK);
                holder.tvTime.setTextColor(Color.BLACK);
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                v.setBackgroundColor(Color.TRANSPARENT);
                holder.tvTitle.setTextColor(Color.WHITE);
                holder.tvTime.setTextColor(Color.parseColor("#B0B0B0"));
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
