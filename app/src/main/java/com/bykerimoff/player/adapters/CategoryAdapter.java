package com.bykerimoff.player.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bykerimoff.player.R;
import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.utils.ThemeManager;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
    private List<Category> categories;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public interface OnCategoryFocusListener {
        void onCategoryFocus(Category category);
    }

    private OnCategoryFocusListener focusListener;

    public void setOnCategoryFocusListener(OnCategoryFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.tvName.setText(category.getName());
        holder.tvCount.setText(String.valueOf(category.getChannelCount()));
        
        int themeColor = ThemeManager.INSTANCE.getThemeColor(holder.itemView.getContext());
        
        holder.itemView.setOnClickListener(v -> listener.onCategoryClick(category));
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_up));
                v.setBackgroundColor(themeColor);
                holder.tvName.setTextColor(Color.BLACK);
                holder.tvCount.setTextColor(Color.BLACK);
                if (focusListener != null) {
                    focusListener.onCategoryFocus(category);
                }
            } else {
                v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.scale_down));
                v.setBackgroundColor(Color.TRANSPARENT);
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvCount.setTextColor(Color.parseColor("#B0B0B0"));
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCount;
        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvCount = itemView.findViewById(R.id.tvChannelCount);
        }
    }
}
