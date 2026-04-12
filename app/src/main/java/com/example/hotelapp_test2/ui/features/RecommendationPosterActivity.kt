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

class RecommendationPosterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster_recommendation)
        setupToolbar(R.string.poster_recommend_title, R.string.toolbar_poster_recommend_subtitle)
        if (!requireRole("admin")) return

        val titleInput = findViewById<TextInputEditText>(R.id.posterRecommendTitle)
        val contentInput = findViewById<TextInputEditText>(R.id.posterRecommendContent)
        val submitButton = findViewById<MaterialButton>(R.id.posterRecommendButton)
        val listText = findViewById<TextView>(R.id.posterRecommendListText)

        fun loadPosters() {
            SupabaseRepository.listPosters(
                type = "recommend",
                limit = 10,
                onSuccess = { posters ->
                    listText.text = if (posters.isEmpty()) {
                        getString(R.string.poster_empty)
                    } else {
                        posters.joinToString("\n") { getString(R.string.poster_list_item, it.title, it.content) }
                    }
                },
                onError = { error -> toast(getString(R.string.error_poster_load, error.message.orEmpty())) }
            )
        }

        loadPosters()

        submitButton.setOnClickListener {
            val title = titleInput.text?.toString().orEmpty().trim()
            val content = contentInput.text?.toString().orEmpty().trim()
            if (title.isBlank() || content.isBlank()) {
                toast(getString(R.string.error_poster_required))
                return@setOnClickListener
            }
            val poster = Poster(
                type = "recommend",
                title = title,
                content = content,
                role = "admin"
            )
            SupabaseRepository.createPoster(
                poster = poster,
                onSuccess = {
                    toast(getString(R.string.success_poster_published))
                    titleInput.setText("")
                    contentInput.setText("")
                    loadPosters()
                },
                onError = { error ->
                    toast(getString(R.string.error_poster_publish, error.message.orEmpty()))
                }
            )
        }
    }
}
