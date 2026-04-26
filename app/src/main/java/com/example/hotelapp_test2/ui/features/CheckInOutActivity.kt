package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class CheckInOutActivity : BaseActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_out)
        setupToolbar(R.string.feature_checkin_title, R.string.toolbar_checkin_subtitle)
        if (!requireRole("admin")) return

        val bookingIdInput = findViewById<TextInputEditText>(R.id.checkInBookingId)
        val roomCodeInput = findViewById<TextInputEditText>(R.id.checkInRoomCode)
        val checkInButton = findViewById<MaterialButton>(R.id.checkInButton)
        val checkOutButton = findViewById<MaterialButton>(R.id.checkOutButton)
        listContainer = findViewById(R.id.checkInListContainer)
        emptyText = findViewById(R.id.checkInEmptyText)

        fun runAction(isCheckIn: Boolean) {
            val bookingId = bookingIdInput.text?.toString().orEmpty().trim()
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            if (bookingId.isBlank() || roomCode.isBlank()) {
                toast(getString(R.string.error_checkin_required))
                return
            }
            updateStay(bookingId, roomCode, isCheckIn)
        }

        checkInButton.setOnClickListener { runAction(true) }
        checkOutButton.setOnClickListener { runAction(false) }
        loadBookings()
    }

    private fun updateStay(bookingId: String, roomCode: String, isCheckIn: Boolean) {
        SupabaseRepository.getRoomByCode(
            code = roomCode,
            onSuccess = { room ->
                val roomId = room?.id?.ifBlank { room.code } ?: roomCode
                val bookingStatus = if (isCheckIn) "checked_in" else "checked_out"
                val roomStatus = if (isCheckIn) "occupied" else "available"
                SupabaseRepository.updateBookingStayStatus(
                    bookingId = bookingId,
                    status = bookingStatus,
                    atMillis = System.currentTimeMillis(),
                    onSuccess = {
                        SupabaseRepository.updateRoomStatus(
                            roomId = roomId,
                            status = roomStatus,
                            onSuccess = {
                                toast(getString(if (isCheckIn) R.string.success_checkin else R.string.success_checkout))
                                loadBookings()
                            },
                            onError = { error -> toast(getString(R.string.error_room_update, error.message.orEmpty())) }
                        )
                    },
                    onError = { error ->
                        toast(getString(if (isCheckIn) R.string.error_checkin else R.string.error_checkout, error.message.orEmpty()))
                    }
                )
            },
            onError = { error -> toast(getString(R.string.error_room_load, error.message.orEmpty())) }
        )
    }

    private fun loadBookings() {
        SupabaseRepository.listBookings(
            userId = null,
            onSuccess = { bookings ->
                val active = bookings.filter { it.status in setOf("confirmed", "paid", "checked_in") }
                renderBookings(active)
            },
            onError = { error -> toast(getString(R.string.error_booking_history, error.message.orEmpty())) }
        )
    }

    private fun renderBookings(bookings: List<Booking>) {
        listContainer.removeAllViews()
        emptyText.visibility = if (bookings.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        bookings.forEach { booking ->
            val card = MaterialCardView(this).apply {
                radius = resources.getDimension(R.dimen.radius_s)
                cardElevation = 0f
                setContentPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            }
            val warning = overdueWarning(booking)
            val text = TextView(this).apply {
                this.text = getString(
                    R.string.checkin_booking_item,
                    booking.id,
                    booking.roomId,
                    booking.checkIn,
                    booking.checkOut,
                    statusLabel(booking.status),
                    warning
                )
                setTextColor(getColor(if (warning.isBlank()) R.color.text_primary else R.color.danger))
                textSize = 14f
            }
            card.addView(text)
            listContainer.addView(card)
        }
    }

    private fun overdueWarning(booking: Booking): String {
        return runCatching {
            val checkout = LocalDate.parse(booking.checkOut)
            if (LocalDate.now().isAfter(checkout) && booking.status != "checked_out") {
                getString(R.string.checkin_overdue_warning)
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun statusLabel(status: String): String = when (status) {
        "confirmed" -> getString(R.string.booking_status_confirmed)
        "paid" -> getString(R.string.booking_status_paid)
        "checked_in" -> getString(R.string.booking_status_checked_in)
        else -> status
    }
}
