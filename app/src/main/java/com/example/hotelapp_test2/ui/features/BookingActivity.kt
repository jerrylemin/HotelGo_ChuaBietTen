package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import android.widget.TextView

class BookingActivity : BaseActivity() {
    private var addOnItems: List<AddOnItem> = emptyList()
    private val selectedAddOnIds = linkedSetOf<String>()

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
        val addOnContainer = findViewById<LinearLayout>(R.id.bookingAddOnContainer)

        loadAddOns(addOnContainer, summaryText)

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
                    val addOnTotal = selectedAddOns().sumOf { it.price }
                    val total = room.price * nights + addOnTotal
                    summaryText.text = getString(R.string.booking_total_with_addons, (room.price * nights).toInt(), addOnTotal.toInt(), total.toInt())

                    val booking = Booking(
                        userId = userId,
                        roomId = room.id,
                        checkIn = checkIn,
                        checkOut = checkOut,
                        status = "pending",
                        total = total,
                        addOns = selectedAddOnIds.toList()
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

    private fun loadAddOns(container: LinearLayout, summaryText: TextView) {
        SupabaseRepository.listAddOns(
            onSuccess = { items ->
                addOnItems = items.filter { it.active }
                container.removeAllViews()
                if (addOnItems.isEmpty()) {
                    val empty = TextView(this).apply {
                        text = getString(R.string.addon_empty)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 13f
                    }
                    container.addView(empty)
                } else {
                    addOnItems.forEach { item ->
                        val checkBox = MaterialCheckBox(this).apply {
                            text = getString(R.string.addon_client_item, item.name, item.price.toInt())
                            isChecked = selectedAddOnIds.contains(item.id)
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedAddOnIds.add(item.id) else selectedAddOnIds.remove(item.id)
                                val addOnTotal = selectedAddOns().sumOf { it.price }
                                summaryText.text = getString(R.string.booking_addon_total_format, addOnTotal.toInt())
                            }
                        }
                        container.addView(checkBox)
                    }
                }
            },
            onError = { error -> toast(getString(R.string.error_addon_load, error.message.orEmpty())) }
        )
    }

    private fun selectedAddOns(): List<AddOnItem> = addOnItems.filter { selectedAddOnIds.contains(it.id) }
}
