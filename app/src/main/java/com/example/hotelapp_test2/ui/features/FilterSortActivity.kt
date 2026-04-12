package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class FilterSortActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_sort)
        setupToolbar(R.string.toolbar_filter_sort_title, R.string.toolbar_filter_sort_subtitle)

        val typeGroup = findViewById<ChipGroup>(R.id.filterTypeGroup)
        val sortGroup = findViewById<ChipGroup>(R.id.filterSortGroup)
        val typeStandard = findViewById<Chip>(R.id.filterTypeStandard)
        val typeDeluxe = findViewById<Chip>(R.id.filterTypeDeluxe)
        val typeSuite = findViewById<Chip>(R.id.filterTypeSuite)
        val sortAsc = findViewById<Chip>(R.id.filterSortAsc)
        val applyButton = findViewById<MaterialButton>(R.id.filterApplyButton)
        val resultText = findViewById<TextView>(R.id.filterResultText)

        applyButton.setOnClickListener {
            val type = when (typeGroup.checkedChipId) {
                typeStandard.id -> "Standard"
                typeDeluxe.id -> "Deluxe"
                typeSuite.id -> "Suite"
                else -> null
            }
            val sortAscending = when (sortGroup.checkedChipId) {
                sortAsc.id -> true
                else -> false
            }
            SupabaseRepository.filterRooms(
                type = type,
                sortAscending = sortAscending,
                onSuccess = { rooms ->
                    resultText.text = if (rooms.isEmpty()) {
                        getString(R.string.filter_empty)
                    } else {
                        rooms.joinToString("\n") { getString(R.string.filter_result_item, it.code, it.type, it.price.toInt()) }
                    }
                },
                onError = { error -> toast(getString(R.string.error_filter, error.message.orEmpty())) }
            )
        }
    }
}
