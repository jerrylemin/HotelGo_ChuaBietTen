package com.example.hotelapp_test2.ui.features

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.model.HotelCatalogItem

class HotelSearchAdapter(
    private val onClick: (HotelCatalogItem) -> Unit
) : ListAdapter<HotelCatalogItem, HotelSearchAdapter.HotelViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hotel_card, parent, false)
        return HotelViewHolder(view)
    }

    override fun onBindViewHolder(holder: HotelViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class HotelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.hotelCardImage)
        private val title: TextView = itemView.findViewById(R.id.hotelCardTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.hotelCardSubtitle)
        private val meta: TextView = itemView.findViewById(R.id.hotelCardMeta)

        fun bind(item: HotelCatalogItem) {
            val context = itemView.context
            title.text = item.displayName.ifBlank { item.name }
            subtitle.text = listOf(item.area, item.city, item.country).filter { it.isNotBlank() }.joinToString(" - ")
            val reviewText = if (item.reviewScore > 0.0) {
                context.getString(R.string.hotel_review_meta, item.reviewScore, item.reviewCount)
            } else {
                context.getString(R.string.hotel_no_review_meta)
            }
            meta.text = context.getString(R.string.hotel_meta_format, item.starRating, reviewText)
            image.load(item.heroImage.ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HotelCatalogItem>() {
            override fun areItemsTheSame(oldItem: HotelCatalogItem, newItem: HotelCatalogItem): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: HotelCatalogItem, newItem: HotelCatalogItem): Boolean = oldItem == newItem
        }
    }
}
