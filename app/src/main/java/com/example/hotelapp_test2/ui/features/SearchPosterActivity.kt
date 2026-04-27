package com.example.hotelapp_test2.ui.features

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.RoomRequest
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
            val email = SupabaseRepository.currentUser()?.email.orEmpty()
            val location = locationInput.text?.toString().orEmpty().trim()
            val roomType = roomTypeInput.text?.toString().orEmpty().trim()
            val budgetText = budgetInput.text?.toString().orEmpty().trim()
            val budget = budgetText.toDoubleOrNull() ?: 0.0
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
                budgetText.ifBlank { getString(R.string.poster_budget_flexible) },
                guests.ifBlank { getString(R.string.common_na) },
                date.ifBlank { getString(R.string.common_na) },
                note.ifBlank { getString(R.string.common_na) }
            )
            val request = RoomRequest(
                userId = userId,
                userEmail = email,
                requestText = getString(R.string.poster_search_generated_title, location) + "\n" + content,
                budget = budget,
                status = "new",
                createdAt = System.currentTimeMillis()
            )
            SupabaseRepository.createRoomRequest(
                request = request,
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
        SupabaseRepository.listRoomRequests(
            userId = if (isAdmin) null else currentUserId,
            onSuccess = { posters ->
                renderPosters(posters)
            },
            onError = { error -> toast(getString(R.string.error_poster_load, error.message.orEmpty())) }
        )
    }

    private fun renderPosters(posters: List<RoomRequest>) {
        listContainer.removeAllViews()
        emptyText.visibility = if (posters.isEmpty()) View.VISIBLE else View.GONE
        posters.forEach { poster ->
            listContainer.addView(createPosterRow(poster))
        }
    }

    private fun createPosterRow(poster: RoomRequest): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_s)
            cardElevation = 0f
            setContentPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            if (isAdmin) setOnClickListener { showAdminResponseDialog(poster) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val detail = TextView(this).apply {
            text = getString(
                R.string.poster_search_item_detail,
                poster.requestText,
                getString(R.string.poster_budget_format, poster.budget.toInt()),
                statusLabel(poster.status),
                poster.adminReply.ifBlank { getString(R.string.poster_response_empty) }
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        content.addView(detail)
        card.addView(content)
        return card
    }

    private fun showAdminResponseDialog(poster: RoomRequest) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }
        val responseInput = TextInputEditText(this).apply {
            hint = getString(R.string.poster_response_hint)
            setText(poster.adminReply)
            minLines = 3
        }
        val spinner = Spinner(this)
        val statuses = listOf("new", "processing", "resolved")
        val labels = statuses.map { statusLabel(it) }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(statuses.indexOf(poster.status).coerceAtLeast(0))
        container.addView(responseInput)
        container.addView(spinner)
        AlertDialog.Builder(this)
            .setTitle(R.string.poster_response_hint)
            .setView(container)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.poster_response_save) { _, _ ->
                updatePoster(poster, statuses[spinner.selectedItemPosition], responseInput.text?.toString().orEmpty().trim())
            }
            .show()
    }

    private fun updatePoster(poster: RoomRequest, status: String, response: String) {
        SupabaseRepository.updateRoomRequest(
            requestId = poster.id,
            status = status,
            adminReply = response,
            onSuccess = {
                toast(getString(R.string.success_poster_response_saved))
                SupabaseRepository.createNotification(
                    AppNotification(
                        userId = poster.userId,
                        userEmail = poster.userEmail,
                        title = getString(R.string.poster_response_notification_title),
                        body = getString(R.string.poster_response_notification_body),
                        type = "room_request",
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
