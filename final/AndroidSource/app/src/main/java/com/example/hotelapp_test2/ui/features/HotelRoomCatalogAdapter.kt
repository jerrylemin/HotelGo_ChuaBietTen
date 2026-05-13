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
import com.example.hotelapp_test2.data.model.HotelCatalogRoom

class HotelRoomCatalogAdapter(
    private val onClick: (HotelCatalogRoom) -> Unit
) : ListAdapter<HotelCatalogRoom, HotelRoomCatalogAdapter.HotelRoomViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelRoomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_card, parent, false)
        return HotelRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: HotelRoomViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class HotelRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.roomCardImage)
        private val title: TextView = itemView.findViewById(R.id.roomCardTitle)
        private val price: TextView = itemView.findViewById(R.id.roomCardPrice)
        private val info: TextView = itemView.findViewById(R.id.roomCardInfo)

        fun bind(room: HotelCatalogRoom) {
            val context = itemView.context
            title.text = room.name
            price.text = if (room.price > 0.0) {
                context.getString(R.string.room_price_per_night, room.price.toInt())
            } else {
                context.getString(R.string.room_price_empty)
            }
            val ratingText = if (room.rating > 0.0) {
                context.getString(R.string.room_rating_format, room.rating)
            } else {
                context.getString(R.string.room_no_rating)
            }
            val extra = listOfNotNull(
                room.view.takeIf { it.isNotBlank() },
                room.shortDescription.takeIf { it.isNotBlank() }
            ).joinToString(" - ")
            val baseInfo = context.getString(R.string.room_info_format, room.capacity, ratingText, statusLabel(room.status, context))
            info.text = if (extra.isBlank()) baseInfo else "$baseInfo - $extra"
            image.load(room.heroImage.ifBlank { room.images.firstOrNull() }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
        }

        private fun statusLabel(status: String, context: android.content.Context): String {
            return when (status) {
                "available" -> context.getString(R.string.status_available)
                "maintenance" -> context.getString(R.string.status_maintenance)
                "occupied" -> context.getString(R.string.status_occupied)
                else -> status
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HotelCatalogRoom>() {
            override fun areItemsTheSame(oldItem: HotelCatalogRoom, newItem: HotelCatalogRoom): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: HotelCatalogRoom, newItem: HotelCatalogRoom): Boolean = oldItem == newItem
        }
    }
}
