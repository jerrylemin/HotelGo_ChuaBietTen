package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.Voucher
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
    private var selectedVoucher: Voucher? = null

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
        val voucherInput = findViewById<TextInputEditText>(R.id.bookingVoucherCode)
        val voucherButton = findViewById<MaterialButton>(R.id.bookingVoucherApplyButton)

        loadAddOns(addOnContainer, summaryText)

        voucherButton.setOnClickListener {
            val code = voucherInput.text?.toString().orEmpty().trim()
            if (code.isBlank()) {
                selectedVoucher = null
                summaryText.text = getString(R.string.voucher_not_found)
                return@setOnClickListener
            }
            SupabaseRepository.getVoucherByCode(
                code = code,
                onSuccess = { voucher ->
                    selectedVoucher = voucher
                    summaryText.text = if (voucher == null) {
                        getString(R.string.voucher_not_found)
                    } else {
                        getString(R.string.voucher_ready, voucher.code)
                    }
                },
                onError = { error -> summaryText.text = getString(R.string.voucher_check_error, error.message.orEmpty()) }
            )
        }

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
                    val roomTotal = room.price * nights
                    val addOnTotal = selectedAddOns().sumOf { it.price }
                    val subtotal = roomTotal + addOnTotal
                    val discount = discountFor(subtotal)
                    if (selectedVoucher != null && discount <= 0.0) {
                        toast(getString(R.string.voucher_invalid))
                        return@getRoomByCode
                    }
                    val total = (subtotal - discount).coerceAtLeast(0.0)
                    summaryText.text = getString(R.string.booking_total_with_discount, roomTotal.toInt(), addOnTotal.toInt(), discount.toInt(), total.toInt())

                    val booking = Booking(
                        userId = userId,
                        roomId = room.id,
                        checkIn = checkIn,
                        checkOut = checkOut,
                        status = "pending",
                        total = total,
                        addOns = selectedAddOnIds.toList() + listOfNotNull(selectedVoucher?.code?.takeIf { it.isNotBlank() }?.let { "voucher:$it" })
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
                            SupabaseRepository.createNotification(
                                AppNotification(
                                    title = getString(R.string.booking_client_notification_title),
                                    body = getString(R.string.booking_client_notification_body, roomCode),
                                    targetRole = "client"
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

    private fun discountFor(subtotal: Double): Double {
        val voucher = selectedVoucher ?: return 0.0
        if (!voucher.active || subtotal < voucher.minSpend || voucher.usageLimit == 0) return 0.0
        val today = LocalDate.now()
        val startOk = runCatching { !today.isBefore(LocalDate.parse(voucher.startAt)) }.getOrDefault(true)
        val endOk = runCatching { !today.isAfter(LocalDate.parse(voucher.endAt)) }.getOrDefault(true)
        if (!startOk || !endOk) return 0.0
        return if (voucher.type.equals("percent", true)) {
            subtotal * voucher.value.coerceIn(0.0, 100.0) / 100.0
        } else {
            voucher.value.coerceAtMost(subtotal)
        }
    }
}
