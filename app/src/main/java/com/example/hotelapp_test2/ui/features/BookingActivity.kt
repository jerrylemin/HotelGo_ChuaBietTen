package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.BookingAddOnSelection
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import android.widget.TextView

class BookingActivity : BaseActivity() {
    private var addOnItems: List<AddOnItem> = emptyList()
    private val selectedAddOnQuantities = linkedMapOf<String, Int>()
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
                    val addOnSelections = selectedAddOns()
                    val addOnTotal = addOnSelections.sumOf { it.totalPrice }
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
                    )
                    SupabaseRepository.createBookingWithAddOns(
                        booking = booking,
                        addOnSelections = addOnSelections,
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
                    addOnItems.forEach { item -> container.addView(createAddOnRow(item, summaryText)) }
                }
            },
            onError = { error -> toast(getString(R.string.error_addon_load, error.message.orEmpty())) }
        )
    }

    private fun createAddOnRow(item: AddOnItem, summaryText: TextView): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_xs), 0, resources.getDimensionPixelSize(R.dimen.space_xs))
        }
        val title = TextView(this).apply {
            text = getString(R.string.addon_client_item_detail, item.name, item.description.ifBlank { getString(R.string.common_na) }, item.price.toInt())
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val minus = MaterialButton(this).apply {
            text = getString(R.string.quantity_decrease)
            minWidth = 0
        }
        val quantityText = TextView(this).apply {
            text = "0"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(resources.getDimensionPixelSize(R.dimen.space_m), resources.getDimensionPixelSize(R.dimen.space_s), resources.getDimensionPixelSize(R.dimen.space_m), 0)
        }
        val plus = MaterialButton(this).apply {
            text = getString(R.string.quantity_increase)
            minWidth = 0
        }
        fun refresh() {
            val qty = selectedAddOnQuantities[item.id] ?: 0
            quantityText.text = qty.toString()
            val addOnTotal = selectedAddOns().sumOf { it.totalPrice }
            summaryText.text = getString(R.string.booking_addon_total_format, addOnTotal.toInt())
        }
        minus.setOnClickListener {
            val next = ((selectedAddOnQuantities[item.id] ?: 0) - 1).coerceAtLeast(0)
            if (next == 0) selectedAddOnQuantities.remove(item.id) else selectedAddOnQuantities[item.id] = next
            refresh()
        }
        plus.setOnClickListener {
            selectedAddOnQuantities[item.id] = ((selectedAddOnQuantities[item.id] ?: 0) + 1).coerceAtMost(20)
            refresh()
        }
        controls.addView(minus)
        controls.addView(quantityText)
        controls.addView(plus)
        row.addView(title)
        row.addView(controls)
        return row
    }

    private fun selectedAddOns(): List<BookingAddOnSelection> = addOnItems.mapNotNull { item ->
        val quantity = selectedAddOnQuantities[item.id] ?: 0
        if (quantity > 0) BookingAddOnSelection(item, quantity) else null
    }

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
