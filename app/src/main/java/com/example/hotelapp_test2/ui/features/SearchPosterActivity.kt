package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Poster
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class SearchPosterActivity : BaseActivity() {
    private var isAdmin = false
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster_search)
        setupToolbar(R.string.poster_search_title, R.string.toolbar_poster_search_subtitle)

        isAdmin = SessionManager.getRole(this) == "admin"
        val formCard = findViewById<MaterialCardView>(R.id.posterSearchFormCard)
        val locationInput = findViewById<TextInputEditText>(R.id.posterSearchLocation)
        val roomTypeInput = findViewById<TextInputEditText>(R.id.posterSearchRoomType)
        val budgetInput = findViewById<TextInputEditText>(R.id.posterSearchBudget)
        val guestsInput = findViewById<TextInputEditText>(R.id.posterSearchGuests)
        val dateInput = findViewById<TextInputEditText>(R.id.posterSearchDate)
        val noteInput = findViewById<TextInputEditText>(R.id.posterSearchNote)
        val submitButton = findViewById<MaterialButton>(R.id.posterSearchButton)
        listContainer = findViewById(R.id.posterSearchListContainer)
        emptyText = findViewById(R.id.posterSearchEmptyText)

        formCard.visibility = if (isAdmin) View.GONE else View.VISIBLE

        loadPosters()

        submitButton.setOnClickListener {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            val location = locationInput.text?.toString().orEmpty().trim()
            val roomType = roomTypeInput.text?.toString().orEmpty().trim()
            val budget = budgetInput.text?.toString().orEmpty().trim()
            val guests = guestsInput.text?.toString().orEmpty().trim()
            val date = dateInput.text?.toString().orEmpty().trim()
            val note = noteInput.text?.toString().orEmpty().trim()
            if (location.isBlank()) {
                toast(getString(R.string.error_poster_location_required))
                return@setOnClickListener
            }
            val content = getString(
                R.string.poster_search_content_detail,
                roomType.ifBlank { getString(R.string.common_na) },
                budget.ifBlank { getString(R.string.poster_budget_flexible) },
                guests.ifBlank { getString(R.string.common_na) },
                date.ifBlank { getString(R.string.common_na) },
                note.ifBlank { getString(R.string.common_na) }
            )
            val poster = Poster(
                type = "search",
                title = getString(R.string.poster_search_generated_title, location),
                content = content,
                userId = userId,
                status = "new",
                response = "",
                role = "client",
                createdAt = System.currentTimeMillis()
            )
            SupabaseRepository.createPoster(
                poster = poster,
                onSuccess = {
                    toast(getString(R.string.success_poster_search_published))
                    SupabaseRepository.createNotification(
                        AppNotification(
                            title = getString(R.string.poster_search_notification_title),
                            body = getString(R.string.poster_search_notification_body, location),
                            targetRole = "admin"
                        ),
                        onSuccess = {},
                        onError = {}
                    )
                    locationInput.setText("")
                    roomTypeInput.setText("")
                    budgetInput.setText("")
                    guestsInput.setText("")
                    dateInput.setText("")
                    noteInput.setText("")
                    loadPosters()
                },
                onError = { error ->
                    toast(getString(R.string.error_poster_publish, error.message.orEmpty()))
                }
            )
        }
    }

    private fun loadPosters() {
        val currentUserId = SupabaseRepository.currentUser()?.uid.orEmpty()
        SupabaseRepository.listPosters(
            type = "search",
            limit = 100,
            onSuccess = { posters ->
                val visible = if (isAdmin) posters else posters.filter { it.userId == currentUserId }
                renderPosters(visible)
            },
            onError = { error -> toast(getString(R.string.error_poster_load, error.message.orEmpty())) }
        )
    }

    private fun renderPosters(posters: List<Poster>) {
        listContainer.removeAllViews()
        emptyText.visibility = if (posters.isEmpty()) View.VISIBLE else View.GONE
        posters.forEach { poster ->
            listContainer.addView(createPosterRow(poster))
        }
    }

    private fun createPosterRow(poster: Poster): MaterialCardView {
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
            text = getString(
                R.string.poster_search_item_detail,
                poster.title,
                poster.content,
                statusLabel(poster.status),
                poster.response.ifBlank { getString(R.string.poster_response_empty) }
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        content.addView(detail)
        if (isAdmin) {
            val responseInput = TextInputEditText(this).apply {
                hint = getString(R.string.poster_response_hint)
                setText(poster.response)
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            }
            listOf(
                "new" to R.string.issue_status_new,
                "processing" to R.string.issue_status_processing,
                "resolved" to R.string.issue_status_resolved
            ).forEach { (status, labelRes) ->
                val button = MaterialButton(this).apply {
                    text = getString(labelRes)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        updatePoster(poster, status, responseInput.text?.toString().orEmpty().trim())
                    }
                }
                actions.addView(button)
            }
            content.addView(responseInput)
            content.addView(actions)
        }
        card.addView(content)
        return card
    }

    private fun updatePoster(poster: Poster, status: String, response: String) {
        SupabaseRepository.createPoster(
            poster = poster.copy(status = status, response = response),
            onSuccess = {
                toast(getString(R.string.success_poster_response_saved))
                SupabaseRepository.createNotification(
                    AppNotification(
                        title = getString(R.string.poster_response_notification_title),
                        body = getString(R.string.poster_response_notification_body),
                        targetRole = "client"
                    ),
                    onSuccess = {},
                    onError = {}
                )
                loadPosters()
            },
            onError = { error -> toast(getString(R.string.error_poster_publish, error.message.orEmpty())) }
        )
    }

    private fun statusLabel(status: String): String = when (status) {
        "processing" -> getString(R.string.issue_status_processing)
        "resolved" -> getString(R.string.issue_status_resolved)
        else -> getString(R.string.issue_status_new)
    }
}
