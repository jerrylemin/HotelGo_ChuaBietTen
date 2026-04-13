package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class CheckInOutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_out)
        setupToolbar(R.string.feature_checkin_title, R.string.toolbar_checkin_subtitle)
        if (!requireRole("admin")) return

        val bookingIdInput = findViewById<TextInputEditText>(R.id.checkInBookingId)
        val roomCodeInput = findViewById<TextInputEditText>(R.id.checkInRoomCode)
        val checkInButton = findViewById<MaterialButton>(R.id.checkInButton)
        val checkOutButton = findViewById<MaterialButton>(R.id.checkOutButton)

        checkInButton.setOnClickListener {
            val bookingId = bookingIdInput.text?.toString().orEmpty().trim()
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            if (bookingId.isBlank() || roomCode.isBlank()) {
                toast(getString(R.string.error_checkin_required))
                return@setOnClickListener
            }
            SupabaseRepository.updateBookingStatus(
                bookingId = bookingId,
                status = "checked_in",
                onSuccess = {
                    SupabaseRepository.updateRoomStatus(
                        roomId = roomCode,
                        status = "occupied",
                        onSuccess = { toast(getString(R.string.success_checkin)) },
                        onError = { error -> toast(getString(R.string.error_room_update, error.message.orEmpty())) }
                    )
                },
                onError = { error -> toast(getString(R.string.error_checkin, error.message.orEmpty())) }
            )
        }

        checkOutButton.setOnClickListener {
            val bookingId = bookingIdInput.text?.toString().orEmpty().trim()
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            if (bookingId.isBlank() || roomCode.isBlank()) {
                toast(getString(R.string.error_checkin_required))
                return@setOnClickListener
            }
            SupabaseRepository.updateBookingStatus(
                bookingId = bookingId,
                status = "checked_out",
                onSuccess = {
                    SupabaseRepository.updateRoomStatus(
                        roomId = roomCode,
                        status = "available",
                        onSuccess = { toast(getString(R.string.success_checkout)) },
                        onError = { error -> toast(getString(R.string.error_room_update, error.message.orEmpty())) }
                    )
                },
                onError = { error -> toast(getString(R.string.error_checkout, error.message.orEmpty())) }
            )
        }
    }
}
