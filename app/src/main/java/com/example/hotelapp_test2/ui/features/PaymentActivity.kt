package com.example.hotelapp_test2.ui.features

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class PaymentActivity : BaseActivity() {

    private var selectedVoucher: Voucher? = null
    private var selectedBooking: Booking? = null
    private var unpaidBookings: List<Booking> = emptyList()

    // Views
    private lateinit var bookingListCard: MaterialCardView
    private lateinit var bookingListContainer: LinearLayout
    private lateinit var bookingListEmpty: TextView
    private lateinit var bookingListLoading: TextView
    private lateinit var formCard: MaterialCardView
    private lateinit var submitButton: MaterialButton
    private lateinit var summaryText: TextView
    private lateinit var statusText: TextView
    private lateinit var methodGroup: RadioGroup
    private lateinit var qrCard: MaterialCardView
    private lateinit var qrImage: ImageView
    private lateinit var qrBankInfo: TextView
    private lateinit var cardInputContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        setupToolbar(R.string.feature_payment_title, R.string.toolbar_payment_subtitle)

        val isAdmin = SessionManager.getRole(this) == "admin"
        if (isAdmin) {
            toast("Admin does not make payments")
            finish()
            return
        }

        bindViews()
        setupListeners()
        loadUnpaidBookings()
        loadHistory()
    }

    private fun bindViews() {
        bookingListCard = findViewById(R.id.paymentBookingListCard)
        bookingListContainer = findViewById(R.id.paymentBookingListContainer)
        bookingListEmpty = findViewById(R.id.paymentBookingListEmpty)
        bookingListLoading = findViewById(R.id.paymentBookingListLoading)
        formCard = findViewById(R.id.paymentFormCard)
        submitButton = findViewById(R.id.paymentSubmitButton)
        summaryText = findViewById(R.id.paymentSummaryText)
        statusText = findViewById(R.id.paymentStatusText)
        methodGroup = findViewById(R.id.paymentMethodGroup)
        qrCard = findViewById(R.id.paymentQrCard)
        qrImage = findViewById(R.id.paymentQrImage)
        qrBankInfo = findViewById(R.id.paymentQrBankInfo)
        cardInputContainer = findViewById(R.id.paymentCardInputContainer)
    }

    private fun setupListeners() {
        methodGroup.setOnCheckedChangeListener { _, checkedId ->
            qrCard.visibility = if (checkedId == R.id.radioMethodQR) View.VISIBLE else View.GONE
            cardInputContainer.visibility = if (checkedId == R.id.radioMethodCard) View.VISIBLE else View.GONE
            submitButton.text = when (checkedId) {
                R.id.radioMethodQR -> getString(R.string.payment_qr_confirm)
                else -> getString(R.string.payment_confirm_button)
            }
            updateSubmitButtonState()
        }

        submitButton.setOnClickListener {
            handlePaymentSubmit()
        }
    }

    private fun updateSubmitButtonState() {
        submitButton.isEnabled = selectedBooking != null && methodGroup.checkedRadioButtonId != -1
    }

    private fun loadUnpaidBookings() {
        val userId = SupabaseRepository.currentUser()?.uid ?: return
        bookingListLoading.visibility = View.VISIBLE
        bookingListContainer.visibility = View.GONE
        bookingListEmpty.visibility = View.GONE

        SupabaseRepository.listBookings(
            userId = userId,
            onSuccess = { allBookings ->
                val unpaidStatuses = setOf("pending", "confirmed", "unpaid")
                unpaidBookings = allBookings.filter { it.status.lowercase() in unpaidStatuses }
                
                bookingListLoading.visibility = View.GONE
                if (unpaidBookings.isEmpty()) {
                    bookingListEmpty.visibility = View.VISIBLE
                } else {
                    bookingListContainer.visibility = View.VISIBLE
                    renderBookingList()
                }
            },
            onError = { error ->
                bookingListLoading.visibility = View.GONE
                bookingListEmpty.visibility = View.VISIBLE
                bookingListEmpty.text = error.message
            }
        )
    }

    private fun renderBookingList() {
        bookingListContainer.removeAllViews()
        for (booking in unpaidBookings) {
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, bookingListContainer, false)
            val titleText = itemView.findViewById<TextView>(android.R.id.text1)
            val detailText = itemView.findViewById<TextView>(android.R.id.text2)
            
            // Get room details
            SupabaseRepository.getRoomByCode(booking.roomId, 
                onSuccess = { room ->
                    runOnUiThread {
                        val roomName = room?.displayType ?: booking.roomId
                        titleText.text = getString(R.string.payment_booking_item_title, booking.id.takeLast(8).uppercase())
                        detailText.text = getString(
                            R.string.payment_booking_item_details,
                            roomName,
                            booking.checkIn,
                            booking.checkOut,
                            booking.status
                        )
                    }
                },
                onError = {
                    titleText.text = getString(R.string.payment_booking_item_title, booking.id.takeLast(8).uppercase())
                    detailText.text = getString(
                        R.string.payment_booking_item_details,
                        booking.roomId,
                        booking.checkIn,
                        booking.checkOut,
                        booking.status
                    )
                }
            )

            val selectButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.payment_booking_select_btn)
                textSize = 12f
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(itemView, params)
                addView(selectButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                })
            }

            selectButton.setOnClickListener {
                selectBooking(booking, row, bookingListContainer)
            }

            bookingListContainer.addView(row)
        }
    }

    private fun selectBooking(booking: Booking, selectedRow: View, container: LinearLayout) {
        selectedBooking = booking
        formCard.visibility = View.VISIBLE
        statusText.text = ""
        
        // Reset button texts
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as LinearLayout
            val btn = row.getChildAt(1) as MaterialButton
            btn.text = getString(R.string.payment_booking_select_btn)
            btn.isEnabled = true
        }

        val btn = selectedRow.findViewById<MaterialButton>(selectedRow.getChildAt(1).id) ?: (selectedRow as LinearLayout).getChildAt(1) as MaterialButton
        btn.text = getString(R.string.payment_booking_selected)
        btn.isEnabled = false

        updateSubmitButtonState()
        
        // Load voucher if code is present in booking, or just summarize
        if (booking.voucherCode.isNotBlank()) {
            SupabaseRepository.getVoucherByCode(
                code = booking.voucherCode,
                onSuccess = { v -> 
                    selectedVoucher = v
                    displaySummary()
                },
                onError = { displaySummary() }
            )
        } else {
            selectedVoucher = null
            displaySummary()
        }
    }

    private fun displaySummary() {
        val booking = selectedBooking ?: return
        val summary = summarizePayment(booking, selectedVoucher)
        summaryText.text = paymentSummaryText(summary)
    }

    private fun loadHistory() {
        val userId = SupabaseRepository.currentUser()?.uid ?: return
        val historyText = findViewById<TextView>(R.id.paymentHistoryText)
        SupabaseRepository.listPayments(
            userId = userId,
            onSuccess = { payments ->
                historyText.text = if (payments.isEmpty()) {
                    getString(R.string.payment_history_empty)
                } else {
                    payments.joinToString("\n\n") {
                        if (it.voucherCode.isBlank() && it.discountAmount <= 0.0) {
                            getString(R.string.payment_history_item, it.bookingId.takeLast(8).uppercase(), it.amount.toInt(), paymentStatusLabel(it.status), it.method)
                        } else {
                            getString(
                                R.string.payment_history_item_discount,
                                it.bookingId.takeLast(8).uppercase(),
                                it.originalTotal.toInt(),
                                it.addonsTotal.toInt(),
                                it.voucherCode.ifBlank { getString(R.string.common_na) },
                                it.discountAmount.toInt(),
                                it.finalTotal.toInt(),
                                paymentStatusLabel(it.status),
                                it.method
                            )
                        }
                    }
                }
            },
            onError = { error -> toast(getString(R.string.error_payment_history, error.message.orEmpty())) }
        )
    }

    private fun handlePaymentSubmit() {
        val userId = SupabaseRepository.currentUser()?.uid ?: return
        val booking = selectedBooking ?: return
        val selectedMethodId = methodGroup.checkedRadioButtonId
        
        val method = when (selectedMethodId) {
            R.id.radioMethodCash -> "CASH"
            R.id.radioMethodQR -> "QR_BANKING"
            R.id.radioMethodCard -> "CARD"
            else -> return
        }

        submitButton.isEnabled = false
        val summary = summarizePayment(booking, selectedVoucher)

        SupabaseRepository.updateBookingPaymentSummary(
            bookingId = booking.id,
            voucher = selectedVoucher,
            originalTotal = summary.originalTotal,
            addonsTotal = summary.addonsTotal,
            discountAmount = summary.discountAmount,
            finalTotal = summary.finalTotal,
            onSuccess = { 
                processPayment(summary.booking, userId, method) 
            },
            onError = { error ->
                submitButton.isEnabled = true
                toast(getString(R.string.error_payment_update_booking, error.message.orEmpty()))
            }
        )
    }

    private fun processPayment(booking: Booking, userId: String, method: String) {
        when (method) {
            "CASH" -> {
                completePayment(booking, userId, method)
            }
            "QR_BANKING" -> {
                val amountVnd = booking.total.toInt()
                val ref = "BOOK-${booking.id.takeLast(8).uppercase()}"
                qrBankInfo.text = getString(R.string.payment_qr_bank_info, amountVnd, ref)
                generateMockQr(qrImage, "HotelGo|$ref|$amountVnd|9876543210")
                qrCard.visibility = View.VISIBLE
                completePayment(booking, userId, method)
            }
            "CARD" -> {
                statusText.text = getString(R.string.payment_card_processing)
                Handler(Looper.getMainLooper()).postDelayed({
                    completePayment(booking, userId, method)
                }, 1500)
            }
        }
    }

    private fun completePayment(booking: Booking, userId: String, method: String) {
        val cardLast4 = if (method == "CARD") {
            val cardNum = findViewById<TextInputEditText>(R.id.paymentCardNumber)
            cardNum.text?.toString().orEmpty().replace(" ", "").takeLast(4)
        } else ""

        val payment = Payment(
            id = "${method.lowercase()}_${booking.id}_${System.currentTimeMillis()}",
            bookingId = booking.id,
            userId = userId,
            amount = booking.total,
            method = method,
            status = if (method == "CASH") "pending" else "paid",
            cardLast4 = cardLast4,
            voucherId = booking.voucherId,
            voucherCode = booking.voucherCode,
            discountAmount = booking.discountAmount,
            originalTotal = booking.originalTotal,
            addonsTotal = booking.addonsTotal,
            finalTotal = booking.total,
            createdAt = System.currentTimeMillis()
        )
        
        SupabaseRepository.createPayment(
            payment = payment,
            onSuccess = {
                val bookingStatus = if (method == "CASH") "confirmed" else "paid"
                SupabaseRepository.updateBookingStatus(
                    bookingId = booking.id,
                    status = bookingStatus,
                    onSuccess = {
                        SupabaseRepository.createNotification(
                            AppNotification(
                                title = getString(R.string.payment_notification_title),
                                body = getString(R.string.payment_notification_body, booking.id.takeLast(8).uppercase()),
                                targetRole = "admin"
                            ),
                            onSuccess = {}, onError = {}
                        )
                        SupabaseRepository.createNotification(
                            AppNotification(
                                title = getString(R.string.payment_client_notification_title),
                                body = getString(R.string.payment_client_notification_body, booking.id.takeLast(8).uppercase()),
                                targetRole = "client"
                            ),
                            onSuccess = {}, onError = {}
                        )
                        if (booking.voucherId.isNotBlank()) {
                            SupabaseRepository.recordVoucherUsage(payment, onSuccess = {}, onError = {})
                            SupabaseRepository.incrementVoucherUsage(booking.voucherId, onSuccess = {}, onError = {})
                        }
                        
                        statusText.text = when (method) {
                            "CASH" -> getString(R.string.payment_cash_success)
                            "CARD" -> getString(R.string.payment_card_success)
                            else -> getString(R.string.success_payment_completed)
                        }
                        showCompletionPopup(
                            NotificationSettings.CATEGORY_PAYMENT,
                            R.string.completion_title,
                            R.string.completion_payment_paid
                        ) {
                            formCard.visibility = View.GONE
                            selectedBooking = null
                            methodGroup.clearCheck()
                            loadUnpaidBookings()
                            loadHistory()
                        }
                    },
                    onError = { error ->
                        submitButton.isEnabled = true
                        toast(getString(R.string.error_payment_update_booking, error.message.orEmpty()))
                    }
                )
            },
            onError = { error ->
                submitButton.isEnabled = true
                toast(getString(R.string.error_payment_save, error.message.orEmpty()))
            }
        )
    }

    private fun generateMockQr(imageView: ImageView, data: String) {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hash = data.hashCode()
        val rng = java.util.Random(hash.toLong())
        val moduleSize = 8
        val modules = size / moduleSize
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                val isFinderPattern = (x < 7 && y < 7) || (x >= modules - 7 && y < 7) || (x < 7 && y >= modules - 7)
                val isBorder = isFinderPattern && (x == 0 || y == 0 || x == 6 || y == 6 || x == modules - 1 || y == modules - 1 || x == modules - 7 || y == modules - 7)
                val isInner = isFinderPattern && (x in 2..4 && y in 2..4) || (x in (modules - 5)..(modules - 3) && y in 2..4) || (x in 2..4 && y in (modules - 5)..(modules - 3))
                val color = when {
                    isBorder || isInner -> Color.BLACK
                    isFinderPattern -> Color.WHITE
                    else -> if (rng.nextBoolean()) Color.BLACK else Color.WHITE
                }
                for (dy in 0 until moduleSize) {
                    for (dx in 0 until moduleSize) {
                        val px = x * moduleSize + dx
                        val py = y * moduleSize + dy
                        if (px < size && py < size) {
                            bitmap.setPixel(px, py, color)
                        }
                    }
                }
            }
        }
        imageView.setImageBitmap(bitmap)
    }

    private data class PaymentSummary(
        val booking: Booking,
        val originalTotal: Double,
        val addonsTotal: Double,
        val subtotal: Double,
        val voucher: Voucher?,
        val discountAmount: Double,
        val finalTotal: Double
    )

    private fun summarizePayment(booking: Booking, voucher: Voucher?): PaymentSummary {
        val addonsTotal = booking.addonsTotal.takeIf { it > 0.0 } ?: booking.addOnDetails.sumOf { it.totalPrice }
        val originalTotal = booking.originalTotal.takeIf { it > 0.0 } ?: (booking.total - addonsTotal).coerceAtLeast(0.0)
        val subtotal = originalTotal + addonsTotal
        val discount = voucher?.let { discountFor(it, subtotal) } ?: booking.discountAmount
        val finalTotal = if (voucher == null && booking.finalTotal > 0.0) booking.finalTotal else (subtotal - discount).coerceAtLeast(0.0)
        return PaymentSummary(
            booking = booking.copy(
                total = finalTotal,
                voucherId = voucher?.id ?: booking.voucherId,
                voucherCode = voucher?.code ?: booking.voucherCode,
                discountAmount = discount,
                originalTotal = originalTotal,
                addonsTotal = addonsTotal,
                finalTotal = finalTotal
            ),
            originalTotal = originalTotal,
            addonsTotal = addonsTotal,
            subtotal = subtotal,
            voucher = voucher,
            discountAmount = discount,
            finalTotal = finalTotal
        )
    }

    private fun paymentSummaryText(summary: PaymentSummary): String = getString(
        R.string.payment_booking_item_summary,
        summary.originalTotal.toInt(),
        summary.addonsTotal.toInt(),
        summary.discountAmount.toInt(),
        summary.finalTotal.toInt()
    )

    private fun discountFor(voucher: Voucher?, subtotal: Double): Double {
        voucher ?: return 0.0
        if (!voucher.active || subtotal < voucher.minSpend) return 0.0
        if (voucher.usageLimit > 0 && voucher.usedCount >= voucher.usageLimit) return 0.0
        val today = LocalDate.now()
        val startOk = runCatching { !today.isBefore(LocalDate.parse(voucher.startAt)) }.getOrDefault(true)
        val endOk = runCatching { !today.isAfter(LocalDate.parse(voucher.endAt)) }.getOrDefault(true)
        if (!startOk || !endOk) return 0.0
        val rawDiscount = if (voucher.type.equals("percent", true) || voucher.type.equals("percentage", true)) {
            subtotal * voucher.value.coerceIn(0.0, 100.0) / 100.0
        } else {
            voucher.value.coerceAtMost(subtotal)
        }
        val cappedDiscount = if (voucher.maxDiscountAmount > 0) rawDiscount.coerceAtMost(voucher.maxDiscountAmount) else rawDiscount
        return cappedDiscount.coerceIn(0.0, subtotal)
    }

    private fun paymentStatusLabel(status: String): String = when (status) {
        "pending" -> getString(R.string.payment_status_pending)
        "paid" -> getString(R.string.payment_status_paid)
        "refunded" -> getString(R.string.payment_status_refunded)
        else -> status
    }
}
