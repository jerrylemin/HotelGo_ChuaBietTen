package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.PayOSGateway
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
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class PaymentActivity : BaseActivity() {
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

        paymentFormCard.visibility = if (isAdmin) View.GONE else View.VISIBLE
        amountInput.isEnabled = false

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
                    val code = voucherInput.text?.toString().orEmpty().trim()
                    if (code.isBlank()) {
                        val summary = summarizePayment(booking, null)
                        amountInput.setText(summary.finalTotal.toInt().toString())
                        summaryText.text = paymentSummaryText(summary)
                        startPayment(summary.booking, userId, submitButton, statusText) { loadHistory() }
                    } else {
                        SupabaseRepository.getVoucherByCode(
                            code = code,
                            onSuccess = { voucher ->
                                if (voucher == null) {
                                    submitButton.isEnabled = true
                                    summaryText.text = getString(R.string.voucher_invalid)
                                    return@getVoucherByCode
                                }
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
                                                onSuccess = { startPayment(summary.booking, userId, submitButton, statusText) { loadHistory() } },
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
                            },
                            onError = { error ->
                                submitButton.isEnabled = true
                                summaryText.text = getString(R.string.voucher_check_error, error.message.orEmpty())
                            }
                        )
                    }
                },
                onError = { error ->
                    submitButton.isEnabled = true
                    toast(getString(R.string.error_booking_history, error.message.orEmpty()))
                }
            )
        }
    }

    private fun startPayment(
        booking: Booking,
        userId: String,
        submitButton: MaterialButton,
        statusText: TextView,
        onDone: () -> Unit
    ) {
        PayOSGateway.createPaymentLinkDemo(
            bookingId = booking.id,
            amountVnd = booking.total.toLong(),
            itemName = getString(R.string.payment_item_booking),
            onSuccess = { link ->
                val pendingPayment = Payment(
                    id = "payos_${link.orderCode}",
                    bookingId = booking.id,
                    userId = userId,
                    amount = booking.total,
                    method = "payOS_DEMO",
                    status = "pending",
                    cardLast4 = link.orderCode.toString().takeLast(4),
                    voucherId = booking.voucherId,
                    voucherCode = booking.voucherCode,
                    discountAmount = booking.discountAmount,
                    originalTotal = booking.originalTotal,
                    addonsTotal = booking.addonsTotal,
                    finalTotal = booking.total,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseRepository.createPayment(
                    payment = pendingPayment,
                    onSuccess = {
                        openPayOSCheckout(link.checkoutUrl)
                        submitButton.isEnabled = true
                        statusText.text = getString(R.string.payment_pending_payos)
                        onDone()
                    },
                    onError = {
                        openPayOSCheckout(link.checkoutUrl)
                        submitButton.isEnabled = true
                    }
                )
            },
            onError = {
                completeDemoPayment(booking, userId, submitButton, statusText, onDone)
            }
        )
    }

    private fun completeDemoPayment(
        booking: Booking,
        userId: String,
        submitButton: MaterialButton,
        statusText: TextView,
        onDone: () -> Unit
    ) {
        val payment = Payment(
            id = "demo_${booking.id}_${System.currentTimeMillis()}",
            bookingId = booking.id,
            userId = userId,
            amount = booking.total,
            method = "DEMO",
            status = "paid",
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
                SupabaseRepository.updateBookingStatus(
                    bookingId = booking.id,
                    status = "paid",
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
                        statusText.text = getString(R.string.success_payment_demo_paid)
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

    private fun openPayOSCheckout(url: String) {
        if (url.isBlank()) {
            toast(getString(R.string.error_payment_url_missing))
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        toast(getString(R.string.success_payment_opened))
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
