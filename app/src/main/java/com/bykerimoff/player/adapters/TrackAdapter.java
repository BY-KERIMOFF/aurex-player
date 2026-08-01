package com.bykerimoff.player.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.media3.common.Tracks;
import androidx.recyclerview.widget.RecyclerView;
import com.bykerimoff.player.R;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.ViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(TrackInfo track);
    }

    public static class TrackInfo {
        public String name;
        public Tracks.Group group;
        public int trackIndex;
        public boolean isSelected;

        public TrackInfo(String name, Tracks.Group group, int trackIndex, boolean isSelected) {
            this.name = name;
            this.group = group;
            this.trackIndex = trackIndex;
            this.isSelected = isSelected;
        }
    }

    private final List<TrackInfo> tracks;
    private final OnTrackClickListener listener;

    public TrackAdapter(List<TrackInfo> tracks, OnTrackClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackInfo track = tracks.get(position);
        holder.tvName.setText(track.name);
        holder.ivCheck.setVisibility(track.isSelected ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onTrackClick(track));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivCheck;
        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTrackName);
            ivCheck = itemView.findViewById(R.id.ivCheck);
        }
    }
}
