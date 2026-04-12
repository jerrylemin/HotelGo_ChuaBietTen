package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Payment
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast

class PayOSReturnActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePayOSResult(intent?.data)
    }

    private fun handlePayOSResult(uri: Uri?) {
        if (uri == null) {
            toast(getString(R.string.error_payos_result_missing))
            goMain()
            return
        }

        val bookingId = uri.getQueryParameter("bookingId").orEmpty()
        val orderCode = uri.getQueryParameter("orderCode").orEmpty()
        val amount = uri.getQueryParameter("amount")?.toDoubleOrNull() ?: 0.0
        val status = uri.getQueryParameter("status").orEmpty().uppercase()
        val isCancelled = uri.host == "payos-cancel" ||
            uri.getQueryParameter("cancel").equals("true", ignoreCase = true)

        if (bookingId.isBlank()) {
            toast(getString(R.string.error_payos_booking_missing))
            goMain()
            return
        }

        if (isCancelled || status == "CANCELLED") {
            toast(getString(R.string.payos_cancelled))
            goMain()
            return
        }

        if (status != "PAID") {
            toast(getString(R.string.payos_not_complete, status))
            goMain()
            return
        }

        val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        val paymentId = if (orderCode.isBlank()) "" else "payos_$orderCode"
        val payment = Payment(
            id = paymentId,
            bookingId = bookingId,
            userId = userId,
            amount = amount,
            method = "payOS_DEMO",
            status = "paid",
            cardLast4 = orderCode.takeLast(4)
        )

        SupabaseRepository.createPayment(
            payment = payment,
            onSuccess = {
                SupabaseRepository.updateBookingStatus(
                    bookingId = bookingId,
                    status = "paid",
                    onSuccess = {
                        toast(getString(R.string.payos_success))
                        goMain()
                    },
                    onError = {
                        toast(getString(R.string.payos_update_error))
                        goMain()
                    }
                )
            },
            onError = {
                toast(getString(R.string.payos_save_error))
                goMain()
            }
        )
    }

    private fun goMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
