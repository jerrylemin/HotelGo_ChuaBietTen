package com.example.hotelapp_test2.ui.features

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.Room
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RecyclerView adapter for the Admin Check-in / Check-out screen.
 * Shows each booking as a selectable card with stay status badge.
 */
class AdminBookingAdapter(
    private val onBookingSelected: (Booking) -> Unit
) : ListAdapter<Booking, AdminBookingAdapter.ViewHolder>(DIFF) {

    var selectedBookingId: String = ""
    var roomLookup: Map<String, Room> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Booking>() {
            override fun areItemsTheSame(old: Booking, new: Booking) = old.id == new.id
            override fun areContentsTheSame(old: Booking, new: Booking) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_booking_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val booking = getItem(position)
        holder.bind(booking, booking.id == selectedBookingId, roomLookup) {
            selectedBookingId = booking.id
            notifyDataSetChanged()
            onBookingSelected(booking)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.adminBookingCard)
        private val codeText: TextView = itemView.findViewById(R.id.adminBookingCode)
        private val statusBadge: TextView = itemView.findViewById(R.id.adminBookingStatusBadge)
        private val roomText: TextView = itemView.findViewById(R.id.adminBookingRoom)
        private val guestText: TextView = itemView.findViewById(R.id.adminBookingGuest)
        private val datesText: TextView = itemView.findViewById(R.id.adminBookingDates)
        private val overdueText: TextView = itemView.findViewById(R.id.adminBookingOverdue)

        fun bind(
            booking: Booking,
            isSelected: Boolean,
            roomLookup: Map<String, Room>,
            onClick: () -> Unit
        ) {
            val ctx = itemView.context
            val stayStatus = SupabaseRepository.resolveStayStatus(booking)

            // Booking short code
            codeText.text = SupabaseRepository.shortBookingCode(booking.id)

            // Room name
            val room = roomLookup[booking.roomId] ?: roomLookup[booking.roomId.substringAfterLast(":")]
            val roomLabel = if (room != null) {
                val hotelPart = room.hotelName.toDisplayWords(3)
                val typePart = room.displayType.ifBlank { room.type }.toDisplayWords(3)
                listOf(hotelPart, typePart).filter { it.isNotBlank() }.joinToString(" - ")
                    .ifBlank { SupabaseRepository.displayRoomName(booking.roomId) }
            } else {
                SupabaseRepository.displayRoomName(booking.roomId)
            }
            roomText.text = roomLabel

            // Guest name
            val guest = booking.guestName.ifBlank {
                ctx.getString(R.string.checkin_guest_unknown, shortId(booking.userId))
            }.let { name ->
                ctx.getString(R.string.client_contact_format, name, booking.guestPhone.ifBlank { ctx.getString(R.string.common_na) })
            }
            guestText.text = ctx.getString(R.string.checkin_guest_label, guest)

            // Dates
            datesText.text = ctx.getString(
                R.string.checkin_dates_format,
                shortDate(booking.checkIn),
                shortDate(booking.checkOut)
            )

            // Overdue warning
            val isOverdue = stayStatus == "overdue"
            overdueText.visibility = if (isOverdue) View.VISIBLE else View.GONE

            // Status badge text and color
            val (badgeLabel, badgeColor) = stayStatusLabel(stayStatus, ctx)
            statusBadge.text = badgeLabel
            statusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)

            // Card highlight when selected
            if (isSelected) {
                card.strokeColor = ContextCompat.getColor(ctx, R.color.brand_primary)
                card.strokeWidth = 4
            } else {
                card.strokeColor = ContextCompat.getColor(ctx, R.color.surface_muted)
                card.strokeWidth = 1
            }

            card.setOnClickListener { onClick() }
        }

        private fun stayStatusLabel(stayStatus: String, ctx: android.content.Context): Pair<String, Int> {
            return when (stayStatus) {
                "pending_checkin" -> Pair(
                    ctx.getString(R.string.stay_status_pending_checkin),
                    ContextCompat.getColor(ctx, R.color.brand_secondary)
                )
                "checked_in" -> Pair(
                    ctx.getString(R.string.stay_status_checked_in),
                    ContextCompat.getColor(ctx, R.color.brand_primary)
                )
                "checked_out" -> Pair(
                    ctx.getString(R.string.stay_status_checked_out),
                    ContextCompat.getColor(ctx, R.color.text_secondary)
                )
                "overdue" -> Pair(
                    ctx.getString(R.string.stay_status_overdue),
                    ContextCompat.getColor(ctx, R.color.danger)
                )
                "cancelled" -> Pair(
                    ctx.getString(R.string.stay_status_cancelled),
                    ContextCompat.getColor(ctx, R.color.text_secondary)
                )
                else -> Pair(stayStatus, ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }

        private fun shortDate(value: String): String =
            runCatching {
                LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yy"))
            }.getOrDefault(value)

        private fun shortId(value: String): String {
            val clean = value.replace("-", "").take(6).uppercase()
            return if (clean.isBlank()) "?" else clean
        }

        private fun String.toDisplayWords(maxWords: Int): String =
            substringAfterLast(":")
                .replace('_', ' ')
                .replace('-', ' ')
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(maxWords)
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
    }
}
