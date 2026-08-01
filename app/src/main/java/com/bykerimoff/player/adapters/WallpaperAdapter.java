package com.bykerimoff.player.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bykerimoff.player.R;
import com.bykerimoff.player.utils.WallpaperManager;

import java.util.List;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.ViewHolder> {

    private final List<WallpaperManager.WallpaperItem> wallpapers;
    private final OnWallpaperClickListener listener;
    private int selectedIndex;

    public interface OnWallpaperClickListener {
        void onWallpaperClick(int index);
    }

    public WallpaperAdapter(List<WallpaperManager.WallpaperItem> wallpapers, int selectedIndex, OnWallpaperClickListener listener) {
        this.wallpapers = wallpapers;
        this.selectedIndex = selectedIndex;
        this.listener = listener;
    }

    public void setSelectedIndex(int index) {
        int oldIndex = selectedIndex;
        this.selectedIndex = index;
        notifyItemChanged(oldIndex);
        notifyItemChanged(selectedIndex);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WallpaperManager.WallpaperItem item = wallpapers.get(position);
        holder.tvName.setText(item.getName());

        if (item.getResId() != null) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getResId())
                    .centerCrop()
                    .into(holder.ivPreview);
        } else if (item.getImageUrl() != null) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.app_background)
                    .centerCrop()
                    .into(holder.ivPreview);
        }

        holder.selectionOverlay.setVisibility(position == selectedIndex ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> listener.onWallpaperClick(position));
        
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setScaleX(1.1f);
                v.setScaleY(1.1f);
                v.setElevation(10f);
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                v.setElevation(2f);
            }
        });
    }

    @Override
    public int getItemCount() {
        return wallpapers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPreview;
        TextView tvName;
        View selectionOverlay;

        ViewHolder(View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.ivWallpaperPreview);
            tvName = itemView.findViewById(R.id.tvWallpaperName);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
        }
    }
}
