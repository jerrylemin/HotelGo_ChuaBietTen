package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.content.Intent

class RoomSearchActivity : BaseActivity() {
    private enum class SortOption {
        PRICE_ASC,
        PRICE_DESC,
        RATING_DESC,
        NEWEST,
        CAPACITY_DESC,
        TYPE_AZ
    }

    private var selectedTypeKey: String? = null
    private var selectedSort: SortOption = SortOption.PRICE_ASC
    private lateinit var adapter: RoomSearchAdapter
    private var rebuildingTypeChips = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_search)
        setupToolbar(R.string.room_search_title, R.string.toolbar_room_search_subtitle)

        val queryInput = findViewById<EditText>(R.id.roomSearchQuery)
        val searchButton = findViewById<MaterialButton>(R.id.roomSearchButton)
        val summaryText = findViewById<TextView>(R.id.roomSearchSummary)
        val emptyText = findViewById<TextView>(R.id.roomSearchEmpty)
        val listView = findViewById<RecyclerView>(R.id.roomSearchList)
        val sortInput = findViewById<AutoCompleteTextView>(R.id.roomSearchSort)
        val typeGroup = findViewById<ChipGroup>(R.id.roomSearchTypeGroup)

        adapter = RoomSearchAdapter { room ->
            val intent = Intent(this, RoomDetailActivity::class.java).apply {
                putExtra(RoomDetailActivity.EXTRA_ROOM_ID, room.id)
                putExtra(RoomDetailActivity.EXTRA_ROOM_CODE, room.code)
            }
            startActivity(intent)
        }
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        val sortOptions = listOf(
            getString(R.string.sort_price_asc),
            getString(R.string.sort_price_desc),
            getString(R.string.sort_rating_desc),
            getString(R.string.sort_newest),
            getString(R.string.sort_capacity_desc),
            getString(R.string.sort_type_az)
        )
        sortInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, sortOptions)
        )
        sortInput.setText(sortOptions.first(), false)
        sortInput.setOnItemClickListener { _, _, position, _ ->
            selectedSort = when (position) {
                0 -> SortOption.PRICE_ASC
                1 -> SortOption.PRICE_DESC
                2 -> SortOption.RATING_DESC
                3 -> SortOption.NEWEST
                4 -> SortOption.CAPACITY_DESC
                else -> SortOption.TYPE_AZ
            }
            performSearch(queryInput.text?.toString().orEmpty().trim(), summaryText, emptyText)
        }

        typeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (rebuildingTypeChips) return@setOnCheckedStateChangeListener
            val selectedChip = checkedIds.firstOrNull()?.let { checkedId -> typeGroup.findViewById<Chip>(checkedId) }
            selectedTypeKey = selectedChip?.tag as? String
            performSearch(queryInput.text?.toString().orEmpty().trim(), summaryText, emptyText)
        }

        searchButton.setOnClickListener {
            performSearch(queryInput.text?.toString().orEmpty().trim(), summaryText, emptyText)
        }

        performSearch("", summaryText, emptyText)
    }

    private fun performSearch(query: String, summaryText: TextView, emptyText: TextView) {
        val typeGroup = findViewById<ChipGroup>(R.id.roomSearchTypeGroup)
        SupabaseRepository.searchRooms(
            queryText = query,
            onSuccess = { rooms ->
                rebuildTypeChips(typeGroup, rooms)
                val filtered = if (selectedTypeKey.isNullOrBlank()) {
                    rooms
                } else {
                    rooms.filter { it.typeKey == selectedTypeKey }
                }

                val sorted = when (selectedSort) {
                    SortOption.PRICE_ASC -> filtered.sortedBy { it.price }
                    SortOption.PRICE_DESC -> filtered.sortedByDescending { it.price }
                    SortOption.RATING_DESC -> filtered.sortedByDescending { it.rating }
                    SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
                    SortOption.CAPACITY_DESC -> filtered.sortedByDescending { it.capacity }
                    SortOption.TYPE_AZ -> filtered.sortedBy { it.type.lowercase() }
                }

                summaryText.text = getString(R.string.room_search_summary, sorted.size)
                emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(sorted)
            },
            onError = { error ->
                toast(getString(R.string.error_room_search, error.message.orEmpty()))
            }
        )
    }

    private fun rebuildTypeChips(typeGroup: ChipGroup, rooms: List<Room>) {
        val types = rooms
            .map { it.displayType.ifBlank { it.type } to it.typeKey.ifBlank { it.type.trim().lowercase() } }
            .filter { (label, key) -> label.isNotBlank() && key.isNotBlank() }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        val validSelectedType = selectedTypeKey?.takeIf { key -> types.any { it.second == key } }
        rebuildingTypeChips = true
        typeGroup.removeAllViews()

        val allChip = Chip(this).apply {
            id = View.generateViewId()
            text = getString(R.string.room_type_all)
            isCheckable = true
            isChecked = validSelectedType == null
        }
        typeGroup.addView(allChip)

        types.forEach { (label, key) ->
            typeGroup.addView(
                Chip(this).apply {
                    id = View.generateViewId()
                    text = label
                    tag = key
                    isCheckable = true
                    isChecked = key == validSelectedType
                }
            )
        }

        selectedTypeKey = validSelectedType
        rebuildingTypeChips = false
    }
}


