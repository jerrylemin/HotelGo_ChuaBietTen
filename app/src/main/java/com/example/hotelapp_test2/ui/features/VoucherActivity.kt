package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class VoucherActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher)
        setupToolbar(R.string.feature_voucher_title, R.string.toolbar_voucher_subtitle)

        val codeInput = findViewById<TextInputEditText>(R.id.voucherCodeInput)
        val checkButton = findViewById<MaterialButton>(R.id.voucherCheckButton)
        val statusText = findViewById<TextView>(R.id.voucherStatusText)
        val listText = findViewById<TextView>(R.id.voucherListText)

        val createCard = findViewById<View>(R.id.voucherCreateCard)
        val role = SessionManager.getRole(this)
        createCard.visibility = if (role == "admin") View.VISIBLE else View.GONE

        val createCode = findViewById<TextInputEditText>(R.id.voucherCreateCode)
        val createType = findViewById<TextInputEditText>(R.id.voucherCreateType)
        val createValue = findViewById<TextInputEditText>(R.id.voucherCreateValue)
        val createMinSpend = findViewById<TextInputEditText>(R.id.voucherCreateMinSpend)
        val createEnd = findViewById<TextInputEditText>(R.id.voucherCreateEnd)
        val createButton = findViewById<MaterialButton>(R.id.voucherCreateButton)

        fun loadVouchers() {
            SupabaseRepository.listVouchers(
                onSuccess = { vouchers ->
                    listText.text = if (vouchers.isEmpty()) {
                        getString(R.string.voucher_empty)
                    } else {
                        vouchers.joinToString("\n") { getString(R.string.voucher_list_item, it.code, it.type, it.value.toString()) }
                    }
                },
                onError = { error -> toast(getString(R.string.error_voucher_load, error.message.orEmpty())) }
            )
        }

        loadVouchers()

        checkButton.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim()
            SupabaseRepository.getVoucherByCode(
                code = code,
                onSuccess = { voucher ->
                    statusText.text = if (voucher == null) {
                        getString(R.string.voucher_not_found)
                    } else {
                        getString(R.string.voucher_found, voucher.code, voucher.value.toString(), voucher.type)
                    }
                },
                onError = { error ->
                    statusText.text = getString(R.string.voucher_check_error, error.message.orEmpty())
                }
            )
        }

        createButton.setOnClickListener {
            if (role != "admin") {
                toast(getString(R.string.error_voucher_admin_only))
                return@setOnClickListener
            }
            val code = createCode.text?.toString().orEmpty().trim()
            val type = createType.text?.toString().orEmpty().trim().ifBlank { "percent" }
            val value = createValue.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            val minSpend = createMinSpend.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            val endAt = createEnd.text?.toString().orEmpty().trim()
            if (code.isBlank() || value <= 0) {
                toast(getString(R.string.error_voucher_required))
                return@setOnClickListener
            }
            val voucher = Voucher(
                id = code,
                code = code,
                type = type,
                value = value,
                minSpend = minSpend,
                startAt = "2026-01-01",
                endAt = endAt.ifBlank { "2026-12-31" },
                active = true,
                usageLimit = 100
            )
            SupabaseRepository.createVoucher(
                voucher = voucher,
                onSuccess = {
                    toast(getString(R.string.success_voucher_created, code))
                    loadVouchers()
                },
                onError = { error ->
                    toast(getString(R.string.error_voucher_create, error.message.orEmpty()))
                }
            )
        }
    }
}
