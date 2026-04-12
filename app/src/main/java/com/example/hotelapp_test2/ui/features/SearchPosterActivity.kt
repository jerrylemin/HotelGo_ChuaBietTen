package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Poster
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SearchPosterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster_search)
        setupToolbar(R.string.poster_search_title, R.string.toolbar_poster_search_subtitle)
        if (!requireRole("client")) return

        val locationInput = findViewById<TextInputEditText>(R.id.posterSearchLocation)
        val budgetInput = findViewById<TextInputEditText>(R.id.posterSearchBudget)
        val submitButton = findViewById<MaterialButton>(R.id.posterSearchButton)
        val listText = findViewById<TextView>(R.id.posterSearchListText)

        fun loadPosters() {
            SupabaseRepository.listPosters(
                type = "search",
                limit = 10,
                onSuccess = { posters ->
                    listText.text = if (posters.isEmpty()) {
                        getString(R.string.poster_search_empty)
                    } else {
                        posters.joinToString("\n") { getString(R.string.poster_list_item, it.title, it.content) }
                    }
                },
                onError = { error -> toast(getString(R.string.error_poster_load, error.message.orEmpty())) }
            )
        }

        loadPosters()

        submitButton.setOnClickListener {
            val location = locationInput.text?.toString().orEmpty().trim()
            val budget = budgetInput.text?.toString().orEmpty().trim()
            if (location.isBlank()) {
                toast(getString(R.string.error_poster_location_required))
                return@setOnClickListener
            }
            val poster = Poster(
                type = "search",
                title = getString(R.string.poster_search_generated_title, location),
                content = getString(R.string.poster_search_generated_content, budget.ifBlank { getString(R.string.poster_budget_flexible) }),
                role = "client"
            )
            SupabaseRepository.createPoster(
                poster = poster,
                onSuccess = {
                    toast(getString(R.string.success_poster_search_published))
                    locationInput.setText("")
                    budgetInput.setText("")
                    loadPosters()
                },
                onError = { error ->
                    toast(getString(R.string.error_poster_publish, error.message.orEmpty()))
                }
            )
        }
    }
}
