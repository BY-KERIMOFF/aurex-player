package com.bykerimoff.player.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.bykerimoff.player.R
import com.bykerimoff.player.databinding.ItemCurrencyBinding
import com.bykerimoff.player.models.CurrencyItem

class CurrencyAdapter(
    private val items: List<CurrencyItem>
) : RecyclerView.Adapter<CurrencyAdapter.CurrencyViewHolder>() {

    class CurrencyViewHolder(val binding: ItemCurrencyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyViewHolder {
        val binding = ItemCurrencyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CurrencyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CurrencyViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvCurrencyCode.text = item.code
            tvCurrencyValue.text = item.value
            tvCurrencyName.text = item.name

            root.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.scale_up))
                    v.elevation = 15f
                } else {
                    v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.scale_down))
                    v.elevation = 0f
                }
            }
            
            // Klik xüsusiyyəti lazım deyil
            root.setOnClickListener(null)
            root.isClickable = false
        }
    }

    override fun getItemCount() = items.size
}
