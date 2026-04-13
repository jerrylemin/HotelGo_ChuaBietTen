package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class AddOnItemsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addon_items)
        setupToolbar(R.string.feature_addon_title, R.string.toolbar_addon_subtitle)
        if (!requireRole("admin")) return

        val nameInput = findViewById<TextInputEditText>(R.id.addOnName)
        val priceInput = findViewById<TextInputEditText>(R.id.addOnPrice)
        val categoryGroup = findViewById<ChipGroup>(R.id.addOnCategoryGroup)
        val categorySnack = findViewById<Chip>(R.id.addOnCategorySnack)
        val saveButton = findViewById<MaterialButton>(R.id.addOnSaveButton)
        val listText = findViewById<TextView>(R.id.addOnListText)

        categorySnack.isChecked = true

        fun loadAddOns() {
            SupabaseRepository.listAddOns(
                onSuccess = { items ->
                    listText.text = if (items.isEmpty()) {
                        getString(R.string.addon_empty)
                    } else {
                        items.joinToString("\n") { getString(R.string.addon_list_item, it.name, it.price.toInt()) }
                    }
                },
                onError = { error -> toast(getString(R.string.error_addon_load, error.message.orEmpty())) }
            )
        }

        loadAddOns()

        saveButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val price = priceInput.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            if (name.isBlank() || price <= 0.0) {
                toast(getString(R.string.error_addon_required))
                return@setOnClickListener
            }
            val category = if (categoryGroup.checkedChipId == categorySnack.id) "snack" else "drink"
            val item = AddOnItem(
                name = name,
                price = price,
                category = category
            )
            SupabaseRepository.createAddOn(
                item = item,
                onSuccess = {
                    toast(getString(R.string.success_addon_saved))
                    nameInput.setText("")
                    priceInput.setText("")
                    loadAddOns()
                },
                onError = { error -> toast(getString(R.string.error_addon_save, error.message.orEmpty())) }
            )
        }
    }
}
