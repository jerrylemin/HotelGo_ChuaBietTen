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
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class PaymentActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        setupToolbar(R.string.feature_payment_title, R.string.toolbar_payment_subtitle)
        val isAdmin = SessionManager.getRole(this) == "admin"

        val paymentFormCard = findViewById<View>(R.id.paymentFormCard)
        val bookingIdInput = findViewById<TextInputEditText>(R.id.paymentBookingId)
        val amountInput = findViewById<TextInputEditText>(R.id.paymentAmount)
        val submitButton = findViewById<MaterialButton>(R.id.paymentSubmitButton)
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
                            getString(R.string.payment_history_item, it.bookingId, it.amount.toInt(), paymentStatusLabel(it.status), it.method)
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
                    amountInput.setText(booking.total.toInt().toString())
                    if (booking.status == "paid") {
                        submitButton.isEnabled = true
                        statusText.text = getString(R.string.payment_already_paid)
                        return@listBookings
                    }
                    startPayment(
                        booking = booking,
                        userId = userId,
                        submitButton = submitButton,
                        statusText = statusText,
                        onDone = { loadHistory() }
                    )
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
            bookingId = booking.id,
            userId = userId,
            amount = booking.total,
            method = "DEMO",
            status = "paid",
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
                        submitButton.isEnabled = true
                        statusText.text = getString(R.string.success_payment_demo_paid)
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

    private fun paymentStatusLabel(status: String): String = when (status) {
        "pending" -> getString(R.string.payment_status_pending)
        "paid" -> getString(R.string.payment_status_paid)
        "refunded" -> getString(R.string.payment_status_refunded)
        else -> status
    }
}
