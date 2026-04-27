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
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class PaymentActivity : BaseActivity() {

    private var selectedVoucher: Voucher? = null
    private var availableVouchers: List<Voucher> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        setupToolbar(R.string.feature_payment_title, R.string.toolbar_payment_subtitle)
        val isAdmin = SessionManager.getRole(this) == "admin"

        val paymentFormCard = findViewById<View>(R.id.paymentFormCard)
        val bookingIdInput = findViewById<TextInputEditText>(R.id.paymentBookingId)
        val amountInput = findViewById<TextInputEditText>(R.id.paymentAmount)
        val voucherInput = findViewById<TextInputEditText>(R.id.paymentVoucherCode)
        val submitButton = findViewById<MaterialButton>(R.id.paymentSubmitButton)
        val summaryText = findViewById<TextView>(R.id.paymentSummaryText)
        val statusText = findViewById<TextView>(R.id.paymentStatusText)
        val historyText = findViewById<TextView>(R.id.paymentHistoryText)
        val methodGroup = findViewById<RadioGroup>(R.id.paymentMethodGroup)
        val qrCard = findViewById<MaterialCardView>(R.id.paymentQrCard)
        val qrImage = findViewById<ImageView>(R.id.paymentQrImage)
        val qrBankInfo = findViewById<TextView>(R.id.paymentQrBankInfo)
        val cardInputContainer = findViewById<View>(R.id.paymentCardInputContainer)
        val voucherLoading = findViewById<TextView>(R.id.paymentVoucherLoading)
        val voucherListContainer = findViewById<LinearLayout>(R.id.paymentVoucherListContainer)
        val selectedVoucherText = findViewById<TextView>(R.id.paymentSelectedVoucherText)

        paymentFormCard.visibility = if (isAdmin) View.GONE else View.VISIBLE
        amountInput.isEnabled = false

        // Payment method radio group toggles QR/Card sections
        methodGroup.setOnCheckedChangeListener { _, checkedId ->
            qrCard.visibility = if (checkedId == R.id.radioMethodQR) View.VISIBLE else View.GONE
            cardInputContainer.visibility = if (checkedId == R.id.radioMethodCard) View.VISIBLE else View.GONE
            submitButton.text = when (checkedId) {
                R.id.radioMethodQR -> getString(R.string.payment_qr_confirm)
                else -> getString(R.string.payment_confirm_button)
            }
        }

        // Load vouchers for user
        if (!isAdmin) {
            loadAvailableVouchers(voucherLoading, voucherListContainer, selectedVoucherText, summaryText, amountInput)
        }

        fun loadHistory() {
            val userId = if (isAdmin) null else SupabaseRepository.currentUser()?.uid.orEmpty()
            SupabaseRepository.listPayments(
                userId = userId,
                onSuccess = { payments ->
                    historyText.text = if (payments.isEmpty()) {
                        getString(R.string.payment_history_empty)
                    } else {
                        payments.joinToString("\n\n") {
                            if (it.voucherCode.isBlank() && it.discountAmount <= 0.0) {
                                getString(R.string.payment_history_item, it.bookingId, it.amount.toInt(), paymentStatusLabel(it.status), it.method)
                            } else {
                                getString(
                                    R.string.payment_history_item_discount,
                                    it.bookingId,
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

        loadHistory()

        submitButton.setOnClickListener {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }

            val bookingId = bookingIdInput.text?.toString().orEmpty().trim()
            if (bookingId.isBlank()) {
                toast(getString(R.string.error_payment_required))
                return@setOnClickListener
            }

            val selectedMethodId = methodGroup.checkedRadioButtonId
            if (selectedMethodId == -1) {
                toast(getString(R.string.payment_method_not_selected))
                return@setOnClickListener
            }

            val method = when (selectedMethodId) {
                R.id.radioMethodCash -> "CASH"
                R.id.radioMethodQR -> "QR_BANKING"
                R.id.radioMethodCard -> "CARD"
                else -> "UNKNOWN"
            }

            submitButton.isEnabled = false
            SupabaseRepository.listBookings(
                userId = userId,
                onSuccess = { bookings ->
                    val booking = bookings.firstOrNull { it.id == bookingId }
                    if (booking == null) {
                        submitButton.isEnabled = true
                        toast(getString(R.string.error_payment_booking_not_found))
                        return@listBookings
                    }
                    if (booking.status == "paid") {
                        submitButton.isEnabled = true
                        statusText.text = getString(R.string.payment_already_paid)
                        return@listBookings
                    }

                    // Get the voucher either from selection or from manual code input
                    val manualCode = voucherInput.text?.toString().orEmpty().trim()
                    val voucherToApply = selectedVoucher ?: if (manualCode.isNotBlank()) null else null

                    if (manualCode.isNotBlank() && voucherToApply == null) {
                        // Look up manual code
                        SupabaseRepository.getVoucherByCode(
                            code = manualCode,
                            onSuccess = { voucher ->
                                if (voucher == null) {
                                    submitButton.isEnabled = true
                                    summaryText.text = getString(R.string.voucher_invalid)
                                    return@getVoucherByCode
                                }
                                processPaymentWithVoucher(booking, voucher, userId, method, submitButton, summaryText, amountInput, statusText, qrImage, qrBankInfo, qrCard) { loadHistory() }
                            },
                            onError = { error ->
                                submitButton.isEnabled = true
                                summaryText.text = getString(R.string.voucher_check_error, error.message.orEmpty())
                            }
                        )
                    } else {
                        // Use selected voucher or no voucher
                        processPaymentWithVoucher(booking, voucherToApply, userId, method, submitButton, summaryText, amountInput, statusText, qrImage, qrBankInfo, qrCard) { loadHistory() }
                    }
                },
                onError = { error ->
                    submitButton.isEnabled = true
                    toast(getString(R.string.error_booking_history, error.message.orEmpty()))
                }
            )
        }
    }

    private fun processPaymentWithVoucher(
        booking: Booking,
        voucher: Voucher?,
        userId: String,
        method: String,
        submitButton: MaterialButton,
        summaryText: TextView,
        amountInput: TextInputEditText,
        statusText: TextView,
        qrImage: ImageView,
        qrBankInfo: TextView,
        qrCard: MaterialCardView,
        onDone: () -> Unit
    ) {
        if (voucher == null) {
            val summary = summarizePayment(booking, null)
            amountInput.setText(summary.finalTotal.toInt().toString())
            summaryText.text = paymentSummaryText(summary)
            processPayment(summary.booking, userId, method, submitButton, statusText, qrImage, qrBankInfo, qrCard, onDone)
        } else {
            SupabaseRepository.hasUserUsedVoucher(
                userId = userId,
                voucher = voucher,
                onSuccess = { used ->
                    val summary = summarizePayment(booking, voucher)
                    if (used || summary.discountAmount <= 0.0) {
                        submitButton.isEnabled = true
                        summaryText.text = getString(R.string.voucher_invalid)
                    } else {
                        amountInput.setText(summary.finalTotal.toInt().toString())
                        summaryText.text = paymentSummaryText(summary)
                        SupabaseRepository.updateBookingPaymentSummary(
                            bookingId = booking.id,
                            voucher = voucher,
                            originalTotal = summary.originalTotal,
                            addonsTotal = summary.addonsTotal,
                            discountAmount = summary.discountAmount,
                            finalTotal = summary.finalTotal,
                            onSuccess = { processPayment(summary.booking, userId, method, submitButton, statusText, qrImage, qrBankInfo, qrCard, onDone) },
                            onError = { error ->
                                submitButton.isEnabled = true
                                toast(getString(R.string.error_payment_update_booking, error.message.orEmpty()))
                            }
                        )
                    }
                },
                onError = { error ->
                    submitButton.isEnabled = true
                    summaryText.text = getString(R.string.voucher_check_error, error.message.orEmpty())
                }
            )
        }
    }

    private fun processPayment(
        booking: Booking,
        userId: String,
        method: String,
        submitButton: MaterialButton,
        statusText: TextView,
        qrImage: ImageView,
        qrBankInfo: TextView,
        qrCard: MaterialCardView,
        onDone: () -> Unit
    ) {
        when (method) {
            "CASH" -> {
                completePayment(booking, userId, method, submitButton, statusText, onDone)
            }
            "QR_BANKING" -> {
                // Show QR mock
                val amountVnd = booking.total.toInt()
                val ref = "BOOK-${booking.id.takeLast(8).uppercase()}"
                qrBankInfo.text = getString(R.string.payment_qr_bank_info, amountVnd, ref)
                generateMockQr(qrImage, "HotelGo|$ref|$amountVnd|9876543210")
                qrCard.visibility = View.VISIBLE
                completePayment(booking, userId, method, submitButton, statusText, onDone)
            }
            "CARD" -> {
                statusText.text = getString(R.string.payment_card_processing)
                Handler(Looper.getMainLooper()).postDelayed({
                    completePayment(booking, userId, method, submitButton, statusText, onDone)
                }, 1500)
            }
            else -> {
                completePayment(booking, userId, method, submitButton, statusText, onDone)
            }
        }
    }

    private fun completePayment(
        booking: Booking,
        userId: String,
        method: String,
        submitButton: MaterialButton,
        statusText: TextView,
        onDone: () -> Unit
    ) {
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
                                body = getString(R.string.payment_notification_body, booking.id),
                                targetRole = "admin"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        SupabaseRepository.createNotification(
                            AppNotification(
                                title = getString(R.string.payment_client_notification_title),
                                body = getString(R.string.payment_client_notification_body, booking.id),
                                targetRole = "client"
                            ),
                            onSuccess = {},
                            onError = {}
                        )
                        SupabaseRepository.recordVoucherUsage(payment, onSuccess = {}, onError = {})
                        SupabaseRepository.incrementVoucherUsage(booking.voucherId, onSuccess = {}, onError = {})
                        submitButton.isEnabled = true
                        statusText.text = when (method) {
                            "CASH" -> getString(R.string.payment_cash_success)
                            "CARD" -> getString(R.string.payment_card_success)
                            else -> getString(R.string.success_payment_completed)
                        }
                        showCompletionPopup(
                            NotificationSettings.CATEGORY_PAYMENT,
                            R.string.completion_title,
                            R.string.completion_payment_paid
                        )
                        onDone()
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

    /** Generate a simple mock QR code bitmap from the given data string */
    private fun generateMockQr(imageView: ImageView, data: String) {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hash = data.hashCode()
        val rng = java.util.Random(hash.toLong())
        // Draw a QR-like pattern
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

    /** Load available vouchers and populate the voucher list UI */
    private fun loadAvailableVouchers(
        loadingText: TextView,
        container: LinearLayout,
        selectedText: TextView,
        summaryText: TextView,
        amountInput: TextInputEditText
    ) {
        loadingText.visibility = View.VISIBLE
        container.visibility = View.GONE
        SupabaseRepository.listVouchers(
            onSuccess = { vouchers ->
                val today = LocalDate.now()
                availableVouchers = vouchers.filter { v ->
                    v.active &&
                        (v.usageLimit <= 0 || v.usedCount < v.usageLimit) &&
                        runCatching { !today.isBefore(LocalDate.parse(v.startAt)) }.getOrDefault(true) &&
                        runCatching { !today.isAfter(LocalDate.parse(v.endAt)) }.getOrDefault(true)
                }
                loadingText.visibility = View.GONE
                if (availableVouchers.isEmpty()) {
                    loadingText.text = getString(R.string.payment_voucher_none)
                    loadingText.visibility = View.VISIBLE
                    return@listVouchers
                }
                container.visibility = View.VISIBLE
                container.removeAllViews()
                for (voucher in availableVouchers) {
                    val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val text1 = itemView.findViewById<TextView>(android.R.id.text1)
                    val text2 = itemView.findViewById<TextView>(android.R.id.text2)

                    val discountLabel = if (voucher.type.equals("percent", true) || voucher.type.equals("percentage", true)) {
                        "${voucher.value.toInt()}%"
                    } else {
                        "${voucher.value.toInt()} VND"
                    }
                    text1.text = "${voucher.code} — $discountLabel"
                    text2.text = voucher.title.ifBlank { voucher.description.ifBlank { getString(R.string.voucher_active) } }

                    val selectButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = getString(R.string.payment_voucher_select)
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
                        selectedVoucher = voucher
                        selectedText.text = getString(R.string.payment_voucher_applied, voucher.code, 0)
                        selectedText.visibility = View.VISIBLE
                        // Clear manual input when selecting from list
                        findViewById<TextInputEditText>(R.id.paymentVoucherCode).setText("")
                    }

                    container.addView(row)
                }

                // Add a "Remove voucher" option
                val removeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.payment_voucher_remove)
                    textSize = 12f
                    setOnClickListener {
                        selectedVoucher = null
                        selectedText.visibility = View.GONE
                    }
                }
                container.addView(removeButton)
            },
            onError = {
                loadingText.text = getString(R.string.payment_voucher_none)
            }
        )
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
        R.string.payment_summary_discount,
        summary.originalTotal.toInt(),
        summary.addonsTotal.toInt(),
        summary.subtotal.toInt(),
        summary.voucher?.code ?: summary.booking.voucherCode.ifBlank { getString(R.string.common_na) },
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
