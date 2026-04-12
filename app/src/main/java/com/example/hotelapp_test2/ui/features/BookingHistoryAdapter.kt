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
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.Room
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BookingHistoryAdapter(
    private val isAdmin: Boolean,
    private val onClientCancel: (Booking) -> Unit,
    private val onAdminConfirm: (Booking) -> Unit,
    private val onAdminCancel: (Booking) -> Unit
) : ListAdapter<Booking, BookingHistoryAdapter.BookingViewHolder>(DIFF) {

    private var roomLookup: Map<String, Room> = emptyMap()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Booking>() {
            override fun areItemsTheSame(oldItem: Booking, newItem: Booking): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Booking, newItem: Booking): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_card, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position), isAdmin, roomLookup, onClientCancel, onAdminConfirm, onAdminCancel)
    }

    fun updateRooms(rooms: Map<String, Room>) {
        roomLookup = rooms
        notifyDataSetChanged()
    }

    class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val roomImage: ImageView = itemView.findViewById(R.id.bookingItemImage)
        private val roomText: TextView = itemView.findViewById(R.id.bookingItemRoom)
        private val priceText: TextView = itemView.findViewById(R.id.bookingItemPrice)
        private val dateText: TextView = itemView.findViewById(R.id.bookingItemDates)
        private val statusText: TextView = itemView.findViewById(R.id.bookingItemStatus)
        private val totalText: TextView = itemView.findViewById(R.id.bookingItemTotal)
        private val userText: TextView = itemView.findViewById(R.id.bookingItemUser)
        private val cancelButton: MaterialButton = itemView.findViewById(R.id.bookingItemCancel)
        private val adminActions: View = itemView.findViewById(R.id.bookingAdminActions)
        private val adminConfirm: MaterialButton = itemView.findViewById(R.id.bookingAdminConfirm)
        private val adminCancel: MaterialButton = itemView.findViewById(R.id.bookingAdminCancel)

        fun bind(
            booking: Booking,
            isAdmin: Boolean,
            roomLookup: Map<String, Room>,
            onClientCancel: (Booking) -> Unit,
            onAdminConfirm: (Booking) -> Unit,
            onAdminCancel: (Booking) -> Unit
        ) {
            val room = roomLookup[booking.roomId] ?: roomLookup[booking.roomId.uppercase()]
                ?: roomLookup[booking.roomId.lowercase()]
            val roomLabel = if (room == null) {
                itemView.context.getString(R.string.booking_room_label, booking.roomId.ifBlank { itemView.context.getString(R.string.common_na) })
            } else {
                val code = room.code.ifBlank { booking.roomId }
                itemView.context.getString(R.string.booking_room_type_label, code, room.type)
            }
            roomText.text = roomLabel
            priceText.text = if (room == null || room.price <= 0.0) {
                itemView.context.getString(R.string.room_price_empty)
            } else {
                itemView.context.getString(R.string.room_price_per_night, room.price.toInt())
            }
            val imageUrl = room?.images?.firstOrNull().orEmpty()
            if (imageUrl.isBlank()) {
                roomImage.setImageResource(R.mipmap.ic_launcher)
            } else {
                roomImage.load(imageUrl) {
                    placeholder(R.mipmap.ic_launcher)
                    error(R.mipmap.ic_launcher)
                    crossfade(true)
                }
            }
            dateText.text = itemView.context.getString(R.string.booking_dates_format, booking.checkIn, booking.checkOut)
            statusText.text = itemView.context.getString(R.string.booking_status_format, statusLabel(booking.status))
            totalText.text = itemView.context.getString(R.string.booking_total_paid, booking.total.toInt())

            if (isAdmin) {
                userText.visibility = View.VISIBLE
                userText.text = itemView.context.getString(R.string.booking_guest_format, booking.userId.ifBlank { itemView.context.getString(R.string.common_na) })
            } else {
                userText.visibility = View.GONE
            }

            val canCancel = !isAdmin &&
                booking.status !in setOf("cancelled", "checked_in", "checked_out") &&
                canCancelByDate(booking.checkIn)
            cancelButton.visibility = if (canCancel) View.VISIBLE else View.GONE
            cancelButton.setOnClickListener { onClientCancel(booking) }

            val canAdminAction = isAdmin && booking.status !in setOf("cancelled", "confirmed", "checked_in", "checked_out")
            adminActions.visibility = if (canAdminAction) View.VISIBLE else View.GONE
            adminConfirm.setOnClickListener { onAdminConfirm(booking) }
            adminCancel.setOnClickListener { onAdminCancel(booking) }
        }

        private fun canCancelByDate(checkIn: String): Boolean {
            return runCatching {
                val today = LocalDate.now()
                val checkInDate = LocalDate.parse(checkIn)
                ChronoUnit.DAYS.between(today, checkInDate) >= 2
            }.getOrDefault(false)
        }

        private fun statusLabel(status: String): String {
            val context = itemView.context
            return when (status) {
                "pending" -> context.getString(R.string.booking_status_pending)
                "confirmed" -> context.getString(R.string.booking_status_confirmed)
                "paid" -> context.getString(R.string.booking_status_paid)
                "checked_in" -> context.getString(R.string.booking_status_checked_in)
                "checked_out" -> context.getString(R.string.booking_status_checked_out)
                "cancelled" -> context.getString(R.string.booking_status_cancelled)
                else -> status
            }
        }
    }
}
