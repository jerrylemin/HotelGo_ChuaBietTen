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
import com.example.hotelapp_test2.data.model.Room

class RoomSearchAdapter(
    private val onClick: (Room) -> Unit
) : ListAdapter<Room, RoomSearchAdapter.RoomViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Room>() {
            override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_card, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener { onClick(getItem(position)) }
    }

    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.roomCardImage)
        private val title: TextView = itemView.findViewById(R.id.roomCardTitle)
        private val price: TextView = itemView.findViewById(R.id.roomCardPrice)
        private val info: TextView = itemView.findViewById(R.id.roomCardInfo)

        fun bind(room: Room) {
            val context = itemView.context
            val code = room.code.ifBlank { room.id.ifBlank { context.getString(R.string.common_na) } }
            val displayType = room.displayType.ifBlank { room.type.ifBlank { context.getString(R.string.room_default_type) } }
            title.text = context.getString(R.string.room_title_format, code, displayType)
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
            val infoLines = buildList {
                if (room.hotelName.isNotBlank()) add(context.getString(R.string.room_detail_hotel_label, room.hotelName))
                add(context.getString(R.string.room_info_format, room.capacity, ratingText, statusLabel(room.status)))
            }
            info.text = infoLines.joinToString("\n")

            val imageUrl = room.images.firstOrNull().orEmpty()
            if (imageUrl.isBlank()) {
                image.setImageResource(R.mipmap.ic_launcher)
            } else {
                image.load(imageUrl) {
                    placeholder(R.mipmap.ic_launcher)
                    error(R.mipmap.ic_launcher)
                    crossfade(true)
                }
            }
        }

        private fun statusLabel(status: String): String {
            val context = itemView.context
            return when (status) {
                "available" -> context.getString(R.string.status_available)
                "maintenance" -> context.getString(R.string.status_maintenance)
                "occupied" -> context.getString(R.string.status_occupied)
                else -> status
            }
        }
    }
}
