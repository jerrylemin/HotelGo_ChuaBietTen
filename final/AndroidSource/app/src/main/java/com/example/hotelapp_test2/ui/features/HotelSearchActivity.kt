package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.textfield.TextInputEditText

class HotelSearchActivity : BaseActivity() {
    private lateinit var adapter: HotelSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotel_search)
        setupToolbar(R.string.hotel_search_title, R.string.hotel_search_subtitle)

        val queryInput = findViewById<TextInputEditText>(R.id.hotelSearchQuery)
        val summary = findViewById<TextView>(R.id.hotelSearchSummary)
        val empty = findViewById<TextView>(R.id.hotelSearchEmpty)
        val listView = findViewById<RecyclerView>(R.id.hotelSearchList)

        adapter = HotelSearchAdapter { hotel ->
            startActivity(
                Intent(this, HotelDetailActivity::class.java)
                    .putExtra(HotelDetailActivity.EXTRA_HOTEL_ID, hotel.id)
            )
        }
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        queryInput.addTextChangedListener {
            searchHotels(it?.toString().orEmpty().trim(), summary, empty)
        }
        searchHotels("", summary, empty)
    }

    private fun searchHotels(query: String, summary: TextView, empty: TextView) {
        SupabaseRepository.searchHotels(
            queryText = query,
            onSuccess = { hotels ->
                adapter.submitList(hotels)
                summary.text = getString(R.string.hotel_search_summary, hotels.size)
                empty.visibility = if (hotels.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { error ->
                toast(getString(R.string.error_hotel_search, error.message.orEmpty()))
            }
        )
    }
}
