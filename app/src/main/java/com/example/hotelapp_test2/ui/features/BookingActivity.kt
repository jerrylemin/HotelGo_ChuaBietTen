package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BookingActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking)
        setupToolbar(R.string.booking_submit, R.string.toolbar_booking_subtitle)
        if (!requireRole("client")) return

        val roomCodeInput = findViewById<TextInputEditText>(R.id.bookingRoomCode)
        val checkInInput = findViewById<TextInputEditText>(R.id.bookingCheckIn)
        val checkOutInput = findViewById<TextInputEditText>(R.id.bookingCheckOut)
        val guestsInput = findViewById<TextInputEditText>(R.id.bookingGuests)
        val submitButton = findViewById<MaterialButton>(R.id.bookingSubmitButton)
        val summaryText = findViewById<TextView>(R.id.bookingSummaryText)

        submitButton.setOnClickListener {
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            val checkIn = checkInInput.text?.toString().orEmpty().trim()
            val checkOut = checkOutInput.text?.toString().orEmpty().trim()
            val guests = guestsInput.text?.toString().orEmpty().toIntOrNull() ?: 1
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()

            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            if (roomCode.isBlank() || checkIn.isBlank() || checkOut.isBlank()) {
                toast(getString(R.string.error_booking_required))
                return@setOnClickListener
            }

            SupabaseRepository.getRoomByCode(
                code = roomCode,
                onSuccess = { room ->
                    if (room == null) {
                        toast(getString(R.string.error_room_not_found, roomCode))
                        return@getRoomByCode
                    }
                    val nights = runCatching {
                        val inDate = LocalDate.parse(checkIn)
                        val outDate = LocalDate.parse(checkOut)
                        val diff = ChronoUnit.DAYS.between(inDate, outDate)
                        if (diff <= 0) 1 else diff
                    }.getOrDefault(1)
                    val total = room.price * nights
                    summaryText.text = getString(R.string.booking_total_format, total.toInt())

                    val booking = Booking(
                        userId = userId,
                        roomId = room.id,
                        checkIn = checkIn,
                        checkOut = checkOut,
                        status = "pending",
                        total = total,
                        addOns = emptyList()
                    )
                    SupabaseRepository.createBooking(
                        booking = booking,
                        onSuccess = {
                            toast(getString(R.string.success_booking_created))
                            SupabaseRepository.createNotification(
                                AppNotification(
                                    title = getString(R.string.booking_notification_title),
                                    body = getString(R.string.booking_notification_body, roomCode),
                                    targetRole = "admin"
                                ),
                                onSuccess = {},
                                onError = {}
                            )
                        },
                        onError = { error ->
                            toast(getString(R.string.error_booking_create, error.message.orEmpty()))
                        }
                    )
                },
                onError = { error ->
                    toast(getString(R.string.error_room_search, error.message.orEmpty()))
                }
            )
        }
    }
}
