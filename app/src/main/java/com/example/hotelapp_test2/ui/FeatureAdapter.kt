package com.example.hotelapp_test2.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.core.FeatureItem
import com.google.android.material.card.MaterialCardView

class FeatureAdapter(
    private val onClick: (FeatureItem) -> Unit
) : ListAdapter<FeatureItem, FeatureAdapter.FeatureViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FeatureItem>() {
            override fun areItemsTheSame(oldItem: FeatureItem, newItem: FeatureItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FeatureItem, newItem: FeatureItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private val badgeColors = intArrayOf(
        R.color.feature_1,
        R.color.feature_2,
        R.color.feature_3,
        R.color.feature_4,
        R.color.feature_5,
        R.color.feature_6,
        R.color.feature_7,
        R.color.feature_8
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feature_card, parent, false)
        return FeatureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
        holder.card.setOnClickListener { onClick(item) }
    }

    inner class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.featureBadge)
        private val title: TextView = itemView.findViewById(R.id.featureTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.featureSubtitle)
        val card: MaterialCardView = itemView.findViewById(R.id.featureCard)

        fun bind(item: FeatureItem, position: Int) {
            val titleText = itemView.context.getString(item.titleRes)
            title.text = titleText
            subtitle.text = itemView.context.getString(item.subtitleRes)

            val firstChar = titleText.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            badge.text = firstChar
            val colorRes = badgeColors[position % badgeColors.size]
            val color = ContextCompat.getColor(itemView.context, colorRes)
            badge.backgroundTintList = ColorStateList.valueOf(color)
        }
    }
}


