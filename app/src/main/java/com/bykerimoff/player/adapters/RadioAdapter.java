package com.bykerimoff.player.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bykerimoff.player.R;
import com.bykerimoff.player.models.RadioStation;
import com.bykerimoff.player.utils.ThemeManager;

import java.util.List;

public class RadioAdapter extends RecyclerView.Adapter<RadioAdapter.ViewHolder> {
    private List<RadioStation> radios;
    private OnRadioClickListener listener;
    private int selectedPosition = -1;

    public interface OnRadioClickListener {
        void onRadioClick(RadioStation radio);
    }

    public RadioAdapter(List<RadioStation> radios, OnRadioClickListener listener) {
        this.radios = radios;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int oldPos = selectedPosition;
        this.selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_radio, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RadioStation radio = radios.get(position);
        holder.tvName.setText(radio.getName());
        holder.tvTags.setText(radio.getTags() != null ? radio.getTags() : "");
        
        int themeColor = ThemeManager.INSTANCE.getThemeColor(holder.itemView.getContext());

        Glide.with(holder.itemView.getContext())
                .load(radio.getLogoUrl())
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .error(android.R.drawable.ic_lock_silent_mode_off)
                .into(holder.ivLogo);

        holder.ivPlaying.setVisibility(position == selectedPosition ? View.VISIBLE : View.GONE);
        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            setSelectedPosition(position);
            listener.onRadioClick(radio);
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_up));
                v.setBackgroundColor(themeColor);
                holder.tvName.setTextColor(Color.BLACK);
                holder.tvTags.setTextColor(Color.BLACK);
            } else {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_down));
                v.setBackgroundColor(Color.TRANSPARENT);
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvTags.setTextColor(Color.parseColor("#B0B0B0"));
            }
        });
    }

    @Override
    public int getItemCount() {
        return radios.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivLogo, ivPlaying;
        TextView tvName, tvTags;
        ViewHolder(View itemView) {
            super(itemView);
            ivLogo = itemView.findViewById(R.id.ivRadioLogo);
            ivPlaying = itemView.findViewById(R.id.ivPlaying);
            tvName = itemView.findViewById(R.id.tvRadioName);
            tvTags = itemView.findViewById(R.id.tvRadioTags);
        }
    }
}
