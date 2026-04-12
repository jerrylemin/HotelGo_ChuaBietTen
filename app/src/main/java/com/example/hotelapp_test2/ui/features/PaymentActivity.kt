package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.PayOSGateway
import com.example.hotelapp_test2.data.SupabaseRepository
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
        if (!requireRole("client")) return

        val bookingIdInput = findViewById<TextInputEditText>(R.id.paymentBookingId)
        val amountInput = findViewById<TextInputEditText>(R.id.paymentAmount)
        val submitButton = findViewById<MaterialButton>(R.id.paymentSubmitButton)

        submitButton.setOnClickListener {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }

            val bookingId = bookingIdInput.text?.toString().orEmpty().trim()
            val amount = amountInput.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            if (bookingId.isBlank() || amount <= 0.0) {
                toast(getString(R.string.error_payment_required))
                return@setOnClickListener
            }

            submitButton.isEnabled = false
            PayOSGateway.createPaymentLinkDemo(
                bookingId = bookingId,
                amountVnd = amount.toLong(),
                itemName = getString(R.string.payment_item_booking),
                onSuccess = { link ->
                    val pendingPayment = Payment(
                        id = "payos_${link.orderCode}",
                        bookingId = bookingId,
                        userId = userId,
                        amount = amount,
                        method = "payOS_DEMO",
                        status = "pending",
                        cardLast4 = link.orderCode.toString().takeLast(4)
                    )
                    SupabaseRepository.createPayment(
                        payment = pendingPayment,
                        onSuccess = {
                            openPayOSCheckout(link.checkoutUrl)
                            submitButton.isEnabled = true
                        },
                        onError = {
                            openPayOSCheckout(link.checkoutUrl)
                            submitButton.isEnabled = true
                        }
                    )
                },
                onError = { error ->
                    submitButton.isEnabled = true
                    toast(getString(R.string.error_payment_link, error.message.orEmpty()))
                }
            )
        }
    }

    private fun openPayOSCheckout(url: String) {
        if (url.isBlank()) {
            toast(getString(R.string.error_payment_url_missing))
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        toast(getString(R.string.success_payment_opened))
    }
}
