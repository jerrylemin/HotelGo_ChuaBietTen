package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.model.Voucher
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class VoucherActivity : BaseActivity() {
    private var editingVoucher: Voucher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher)
        setupToolbar(R.string.feature_voucher_title, R.string.toolbar_voucher_subtitle)

        val codeInput = findViewById<TextInputEditText>(R.id.voucherCodeInput)
        val checkButton = findViewById<MaterialButton>(R.id.voucherCheckButton)
        val statusText = findViewById<TextView>(R.id.voucherStatusText)
        val listContainer = findViewById<LinearLayout>(R.id.voucherListContainer)
        val emptyText = findViewById<TextView>(R.id.voucherEmptyText)

        val createCard = findViewById<View>(R.id.voucherCreateCard)
        val role = SessionManager.getRole(this)
        createCard.visibility = if (role == "admin") View.VISIBLE else View.GONE

        val createCode = findViewById<TextInputEditText>(R.id.voucherCreateCode)
        val createType = findViewById<TextInputEditText>(R.id.voucherCreateType)
        val createValue = findViewById<TextInputEditText>(R.id.voucherCreateValue)
        val createMinSpend = findViewById<TextInputEditText>(R.id.voucherCreateMinSpend)
        val createEnd = findViewById<TextInputEditText>(R.id.voucherCreateEnd)
        val createUsageLimit = findViewById<TextInputEditText>(R.id.voucherCreateUsageLimit)
        val createActive = findViewById<SwitchMaterial>(R.id.voucherCreateActive)
        val createButton = findViewById<MaterialButton>(R.id.voucherCreateButton)
        createActive.isChecked = true

        fun loadVouchers() {
            SupabaseRepository.listVouchers(
                onSuccess = { vouchers ->
                    listContainer.removeAllViews()
                    val visible = if (role == "admin") vouchers else vouchers.filter { it.isUsableFor(0.0) }
                    emptyText.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                    visible.forEach { voucher ->
                        listContainer.addView(createVoucherRow(voucher, role == "admin", onEdit = {
                            editingVoucher = voucher
                            createCode.setText(voucher.code)
                            createType.setText(voucher.type)
                            createValue.setText(voucher.value.toString())
                            createMinSpend.setText(voucher.minSpend.toString())
                            createEnd.setText(voucher.endAt)
                            createUsageLimit.setText(voucher.usageLimit.toString())
                            createActive.isChecked = voucher.active
                            createButton.text = getString(R.string.voucher_update)
                        }, onDelete = {
                            SupabaseRepository.deleteVoucher(
                                voucherId = voucher.id,
                                onSuccess = {
                                    toast(getString(R.string.success_voucher_deleted))
                                    loadVouchers()
                                },
                                onError = { error -> toast(getString(R.string.error_voucher_delete, error.message.orEmpty())) }
                            )
                        }))
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
                    } else if (!voucher.isUsableFor(0.0)) {
                        getString(R.string.voucher_invalid)
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
            val usageLimit = createUsageLimit.text?.toString().orEmpty().toIntOrNull() ?: 100
            if (code.isBlank() || value <= 0) {
                toast(getString(R.string.error_voucher_required))
                return@setOnClickListener
            }
            val voucher = Voucher(
                id = editingVoucher?.id ?: code,
                code = code,
                type = type,
                value = value,
                minSpend = minSpend,
                startAt = "2026-01-01",
                endAt = endAt.ifBlank { "2026-12-31" },
                active = createActive.isChecked,
                usageLimit = usageLimit
            )
            SupabaseRepository.createVoucher(
                voucher = voucher,
                onSuccess = {
                    toast(getString(R.string.success_voucher_created, code))
                    editingVoucher = null
                    createCode.setText("")
                    createType.setText("")
                    createValue.setText("")
                    createMinSpend.setText("")
                    createEnd.setText("")
                    createUsageLimit.setText("")
                    createActive.isChecked = true
                    createButton.text = getString(R.string.voucher_create)
                    loadVouchers()
                },
                onError = { error ->
                    toast(getString(R.string.error_voucher_create, error.message.orEmpty()))
                }
            )
        }
    }

    private fun createVoucherRow(voucher: Voucher, isAdmin: Boolean, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_s)
            cardElevation = 0f
            setContentPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val detail = TextView(this).apply {
            val status = if (voucher.active) getString(R.string.voucher_status_active) else getString(R.string.voucher_status_inactive)
            text = getString(
                R.string.voucher_list_item_detail,
                voucher.code,
                voucher.type,
                voucher.value.toString(),
                voucher.endAt,
                voucher.usageLimit,
                status
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        content.addView(detail)
        if (isAdmin) {
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            }
            val editButton = MaterialButton(this).apply {
                text = getString(R.string.voucher_edit)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onEdit() }
            }
            val deleteButton = MaterialButton(this).apply {
                text = getString(R.string.voucher_delete)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onDelete() }
            }
            actions.addView(editButton)
            actions.addView(deleteButton)
            content.addView(actions)
        }
        card.addView(content)
        return card
    }

    private fun Voucher.isUsableFor(total: Double): Boolean {
        val today = LocalDate.now()
        val expiryOk = runCatching { !today.isAfter(LocalDate.parse(endAt)) }.getOrDefault(true)
        val startOk = runCatching { !today.isBefore(LocalDate.parse(startAt)) }.getOrDefault(true)
        val spendOk = total <= 0.0 || total >= minSpend
        val limitOk = usageLimit != 0
        return active && expiryOk && startOk && spendOk && limitOk
    }
}
