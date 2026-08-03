package com.bykerimoff.player.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bykerimoff.player.R
import com.bykerimoff.player.databinding.ItemResumeBinding
import com.bykerimoff.player.models.ResumeItem

class ResumeAdapter(
    private val items: List<ResumeItem>,
    private val onItemClick: (ResumeItem) -> Unit
) : RecyclerView.Adapter<ResumeAdapter.ResumeViewHolder>() {

    class ResumeViewHolder(val binding: ItemResumeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResumeViewHolder {
        val binding = ItemResumeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResumeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResumeViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvResumeName.text = item.name
            
            val progress = if (item.duration > 0) (item.position * 100 / item.duration).toInt() else 0
            pbResumeProgress.progress = progress

            Glide.with(root.context)
                .load(item.logoUrl)
                .placeholder(R.drawable.default_logo)
                .error(R.drawable.default_logo)
                .into(ivResumeLogo)

            root.setOnClickListener { onItemClick(item) }

            root.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.scale_up))
                    v.elevation = 15f
                    pbResumeProgress.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
                } else {
                    v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.scale_down))
                    v.elevation = 0f
                    pbResumeProgress.progressTintList = android.content.res.ColorStateList.valueOf(v.context.resources.getColor(R.color.gold_primary))
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
