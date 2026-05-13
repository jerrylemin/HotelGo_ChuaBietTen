package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AddOnItem
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class AddOnItemsActivity : BaseActivity() {
    private var editingItem: AddOnItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addon_items)
        setupToolbar(R.string.feature_addon_title, R.string.toolbar_addon_subtitle)
        if (!requireRole("admin")) return

        val nameInput = findViewById<TextInputEditText>(R.id.addOnName)
        val priceInput = findViewById<TextInputEditText>(R.id.addOnPrice)
        val descriptionInput = findViewById<TextInputEditText>(R.id.addOnDescription)
        val imageInput = findViewById<TextInputEditText>(R.id.addOnImageUrl)
        val activeSwitch = findViewById<SwitchMaterial>(R.id.addOnActiveSwitch)
        val categoryGroup = findViewById<ChipGroup>(R.id.addOnCategoryGroup)
        val categorySnack = findViewById<Chip>(R.id.addOnCategorySnack)
        val categoryDrink = findViewById<Chip>(R.id.addOnCategoryDrink)
        val saveButton = findViewById<MaterialButton>(R.id.addOnSaveButton)
        val listContainer = findViewById<LinearLayout>(R.id.addOnListContainer)
        val emptyText = findViewById<TextView>(R.id.addOnEmptyText)

        categorySnack.isChecked = true
        activeSwitch.isChecked = true

        fun loadAddOns() {
            SupabaseRepository.listAddOns(
                onSuccess = { items ->
                    listContainer.removeAllViews()
                    emptyText.text = getString(R.string.addon_empty)
                    emptyText.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    items.forEach { item ->
                        listContainer.addView(createAddOnRow(item, onEdit = {
                            editingItem = item
                            nameInput.setText(item.name)
                            priceInput.setText(item.price.toInt().toString())
                            descriptionInput.setText(item.description)
                            imageInput.setText(item.imageUrl)
                            activeSwitch.isChecked = item.active
                            if (item.category == "drink") categoryDrink.isChecked = true else categorySnack.isChecked = true
                            saveButton.text = getString(R.string.addon_update)
                        }, onDelete = {
                            SupabaseRepository.deleteAddOn(
                                addOnId = item.id,
                                onSuccess = {
                                    toast(getString(R.string.success_addon_deleted))
                                    loadAddOns()
                                },
                                onError = { error -> toast(getString(R.string.error_addon_delete, error.message.orEmpty())) }
                            )
                        }))
                    }
                },
                onError = { error -> toast(getString(R.string.error_addon_load, error.message.orEmpty())) }
            )
        }

        loadAddOns()

        saveButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val price = priceInput.text?.toString().orEmpty().toDoubleOrNull() ?: 0.0
            val description = descriptionInput.text?.toString().orEmpty().trim()
            val imageUrl = imageInput.text?.toString().orEmpty().trim()
            if (name.isBlank() || price <= 0.0) {
                toast(getString(R.string.error_addon_required))
                return@setOnClickListener
            }
            val category = if (categoryGroup.checkedChipId == categorySnack.id) "snack" else "drink"
            val item = AddOnItem(
                id = editingItem?.id.orEmpty(),
                name = name,
                price = price,
                description = description,
                imageUrl = imageUrl,
                category = category,
                active = activeSwitch.isChecked
            )
            SupabaseRepository.createAddOn(
                item = item,
                onSuccess = {
                    toast(getString(R.string.success_addon_saved))
                    editingItem = null
                    nameInput.setText("")
                    priceInput.setText("")
                    descriptionInput.setText("")
                    imageInput.setText("")
                    activeSwitch.isChecked = true
                    categorySnack.isChecked = true
                    saveButton.text = getString(R.string.addon_save)
                    loadAddOns()
                },
                onError = { error -> toast(getString(R.string.error_addon_save, error.message.orEmpty())) }
            )
        }
    }

    private fun createAddOnRow(item: AddOnItem, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView {
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
            val status = if (item.active) getString(R.string.addon_status_active) else getString(R.string.addon_status_inactive)
            text = getString(
                R.string.addon_list_item_detail,
                item.name,
                item.price.toInt(),
                item.description.ifBlank { getString(R.string.common_na) },
                status
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
        }
        val editButton = MaterialButton(this).apply {
            text = getString(R.string.addon_edit)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onEdit() }
        }
        val deleteButton = MaterialButton(this).apply {
            text = getString(R.string.addon_delete)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onDelete() }
        }
        actions.addView(editButton)
        actions.addView(deleteButton)
        content.addView(detail)
        content.addView(actions)
        card.addView(content)
        return card
    }
}
