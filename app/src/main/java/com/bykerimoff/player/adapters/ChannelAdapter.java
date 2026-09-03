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
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bykerimoff.player.R;
import com.bykerimoff.player.models.Channel;
import com.bykerimoff.player.utils.FavoriteManager;
import com.bykerimoff.player.utils.LogoManager;
import com.bykerimoff.player.utils.ThemeManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {
    public static final int VIEW_TYPE_LIST = 0;
    public static final int VIEW_TYPE_GRID = 1;
    public static final int VIEW_TYPE_COMPACT = 2;

    private List<Channel> channels;
    private OnChannelClickListener listener;
    private FavoriteManager favoriteManager;
    private int currentViewType = VIEW_TYPE_LIST;
    private int selectedPosition = -1;
    private boolean isMultiSelectMode = false;
    private final Set<String> markedChannelIds = new HashSet<>();

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public void setMultiSelectMode(boolean multiSelectMode) {
        this.isMultiSelectMode = multiSelectMode;
        if (!multiSelectMode) markedChannelIds.clear();
        notifyDataSetChanged();
    }

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
        void onChannelFocus(Channel channel);
        void onChannelLongClick(Channel channel);
    }

    public void toggleMark(String channelId) {
        if (markedChannelIds.contains(channelId)) {
            markedChannelIds.remove(channelId);
        } else {
            markedChannelIds.add(channelId);
        }
        notifyDataSetChanged();
    }

    public Set<String> getMarkedChannelIds() {
        return markedChannelIds;
    }

    public void clearMarks() {
        markedChannelIds.clear();
        notifyDataSetChanged();
    }

    public ChannelAdapter(List<Channel> channels, OnChannelClickListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    public void updateData(List<Channel> newChannels) {
        this.channels = newChannels;
        notifyDataSetChanged();
    }

    public void setViewType(int viewType) {
        this.currentViewType = viewType;
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        int oldPos = selectedPosition;
        this.selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    @Override
    public int getItemViewType(int position) {
        return currentViewType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (viewType == VIEW_TYPE_GRID) {
            layoutId = R.layout.item_vod;
        } else if (viewType == VIEW_TYPE_COMPACT) {
            layoutId = R.layout.item_channel_compact;
        } else {
            layoutId = R.layout.item_channel;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.tvName.setText(channel.getName());
        
        int themeColor = ThemeManager.INSTANCE.getThemeColor(holder.itemView.getContext());

        if (holder.tvNumber != null) {
            holder.tvNumber.setText(String.valueOf(position + 1));
            holder.tvNumber.setTextColor(themeColor);
        }
        
        if (favoriteManager == null) {
            favoriteManager = new FavoriteManager(holder.itemView.getContext());
        }

        String logoUrl = channel.getLogoUrl();
        if (logoUrl == null || logoUrl.isEmpty()) {
            logoUrl = LogoManager.INSTANCE.getLogoForChannel(channel.getName());
            if (logoUrl != null) {
                channel.setLogoUrl(logoUrl);
            }
        }

        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(logoUrl)
                .format(DecodeFormat.PREFER_RGB_565) // RAM-a 50% qənaət
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Tam keş
                .placeholder(R.drawable.default_logo)
                .error(R.drawable.default_logo)
                .into(holder.ivLogo);

        boolean isFav = favoriteManager.isFavorite(channel.getId());
        holder.ivFavorite.setVisibility(isFav ? View.VISIBLE : View.GONE);

        // Hazırda baxılan kanalın vizual seçilməsi
        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnClickListener(v -> listener.onChannelClick(channel));
        
        holder.itemView.setOnLongClickListener(v -> {
            listener.onChannelLongClick(channel);
            return true;
        });
        
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_up));
                v.setElevation(12f);
                v.setBackgroundColor(themeColor);
                holder.tvName.setTextColor(Color.BLACK);
                if (holder.tvNumber != null) {
                    holder.tvNumber.setTextColor(Color.BLACK);
                }
                listener.onChannelFocus(channel);
            } else {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_down));
                v.setElevation(0f);
                if (markedChannelIds.contains(channel.getId())) {
                    v.setBackgroundColor(Color.parseColor("#80FFD700")); // Semi-transparent Gold for marked
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT);
                }
                holder.tvName.setTextColor(Color.WHITE);
                if (holder.tvNumber != null) {
                    holder.tvNumber.setTextColor(themeColor);
                }
            }
        });

        // Həmçinin bind zamanı rəngi yoxla (əgər focus deyilsə)
        if (!holder.itemView.hasFocus()) {
            if (markedChannelIds.contains(channel.getId())) {
                holder.itemView.setBackgroundColor(Color.parseColor("#80FFD700"));
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // RAM-a qənaət: View ekrandan çıxanda loqonu yaddaşdan təmizlə
        Glide.with(holder.itemView.getContext()).clear(holder.ivLogo);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivLogo, ivFavorite;
        TextView tvName, tvNumber;
        ViewHolder(View itemView) {
            super(itemView);
            ivLogo = itemView.findViewById(R.id.ivLogo);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
            tvName = itemView.findViewById(R.id.tvChannelName);
            tvNumber = itemView.findViewById(R.id.tvChannelNumber);
        }
    }
}
