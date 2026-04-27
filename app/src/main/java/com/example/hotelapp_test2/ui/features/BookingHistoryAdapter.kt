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
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        private val addOnsText: TextView = itemView.findViewById(R.id.bookingItemAddOns)
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
                itemView.context.getString(R.string.booking_room_label, shortId(booking.roomId).ifBlank { itemView.context.getString(R.string.common_na) })
            } else {
                roomDisplayName(room, booking.roomId)
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
            dateText.text = itemView.context.getString(R.string.booking_dates_format, shortDate(booking.checkIn), shortDate(booking.checkOut))
            statusText.text = itemView.context.getString(R.string.booking_status_format, statusLabel(booking.status))
            totalText.text = if (booking.discountAmount > 0.0 || booking.voucherCode.isNotBlank()) {
                itemView.context.getString(
                    R.string.booking_total_discount_detail,
                    money(booking.originalTotal),
                    money(booking.addonsTotal),
                    booking.voucherCode.ifBlank { itemView.context.getString(R.string.common_na) },
                    money(booking.discountAmount),
                    money(booking.total)
                )
            } else {
                itemView.context.getString(R.string.booking_total_paid, money(booking.total))
            }
            addOnsText.text = if (booking.addOnDetails.isNotEmpty()) {
                val details = booking.addOnDetails.joinToString("\n") { addOn ->
                    itemView.context.getString(
                        R.string.booking_addon_detail_line,
                        compactAddOnName(addOn.name.ifBlank { addOn.addOnItemId }),
                        addOn.quantity,
                        money(addOn.unitPrice),
                        money(addOn.totalPrice)
                    )
                }
                val total = booking.addOnDetails.sumOf { it.totalPrice }
                itemView.context.getString(R.string.booking_addons_detail_format, details, money(total))
            } else if (booking.addOns.isEmpty()) {
                itemView.context.getString(R.string.booking_addons_empty)
            } else {
                itemView.context.getString(R.string.booking_addons_format, compactLegacyAddOns(booking.addOns))
            }

            if (isAdmin) {
                userText.visibility = View.VISIBLE
                userText.text = itemView.context.getString(R.string.booking_guest_format, guestLabel(booking.userId))
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

        private fun roomDisplayName(room: Room, fallbackRoomId: String): String {
            val hotel = room.hotelName
                .ifBlank { room.code.substringBefore("-").ifBlank { "" } }
                .toDisplayWords(maxWords = 3)
            val type = room.displayType
                .ifBlank { room.type }
                .ifBlank { fallbackRoomId }
                .toDisplayWords(maxWords = 3)
            return listOf(hotel, type)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
                .ifBlank { shortId(fallbackRoomId) }
        }

        private fun shortDate(value: String): String =
            runCatching {
                LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM"))
            }.getOrDefault(value)

        private fun guestLabel(userId: String): String =
            userId.ifBlank { itemView.context.getString(R.string.common_na) }
                .let { "Khach #${shortId(it)}" }

        private fun money(value: Double): String =
            String.format(Locale.US, "%,d", value.toInt()).replace(',', '.')

        private fun shortId(value: String): String {
            val clean = value.trim()
            if (clean.length <= 8) return clean
            return clean.takeLast(6)
        }

        private fun compactLegacyAddOns(addOns: List<String>): String {
            val names = addOns.mapNotNull { token ->
                val id = token.substringBefore(":").trim()
                val quantity = token.substringAfter(":", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
                compactAddOnName(id).takeIf { it.isNotBlank() }?.let { "$it x$quantity" }
            }
            return when {
                names.isEmpty() -> itemView.context.getString(R.string.common_na)
                names.size <= 2 -> names.joinToString(", ")
                else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
            }
        }

        private fun compactAddOnName(raw: String): String {
            val lower = raw.lowercase(Locale.ROOT)
            return when {
                "drink" in lower || "water" in lower || "nuoc" in lower -> "Nuoc"
                "snack" in lower || "food" in lower || "breakfast" in lower || "bua" in lower -> "Do an"
                "transfer" in lower || "airport" in lower || "dua" in lower -> "Dua don"
                "laundry" in lower || "giat" in lower -> "Giat ui"
                "decoration" in lower || "trang" in lower -> "Trang tri"
                raw.isBlank() -> ""
                raw.length > 16 -> "Dich vu #${shortId(raw)}"
                else -> raw.toDisplayWords(maxWords = 2)
            }
        }

        private fun String.toDisplayWords(maxWords: Int): String =
            replace('_', ' ')
                .replace('-', ' ')
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(maxWords)
                .joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
    }
}
