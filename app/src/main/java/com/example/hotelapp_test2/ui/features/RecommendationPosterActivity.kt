package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Poster
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class RecommendationPosterActivity : BaseActivity() {
    private var editingPoster: Poster? = null
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster_recommendation)
        setupToolbar(R.string.poster_recommend_title, R.string.toolbar_poster_recommend_subtitle)

        isAdmin = SessionManager.getRole(this) == "admin"
        val formCard = findViewById<MaterialCardView>(R.id.posterRecommendFormCard)
        val titleInput = findViewById<TextInputEditText>(R.id.posterRecommendTitle)
        val contentInput = findViewById<TextInputEditText>(R.id.posterRecommendContent)
        val imageInput = findViewById<TextInputEditText>(R.id.posterRecommendImage)
        val roomInput = findViewById<TextInputEditText>(R.id.posterRecommendRoom)
        val activeSwitch = findViewById<SwitchMaterial>(R.id.posterRecommendActive)
        val submitButton = findViewById<MaterialButton>(R.id.posterRecommendButton)
        val emptyText = findViewById<TextView>(R.id.posterRecommendEmptyText)
        val listContainer = findViewById<LinearLayout>(R.id.posterRecommendListContainer)

        formCard.visibility = if (isAdmin) View.VISIBLE else View.GONE
        activeSwitch.isChecked = true

        fun loadPosters() {
            SupabaseRepository.listPosters(
                type = "recommend",
                limit = 50,
                onSuccess = { posters ->
                    val visible = if (isAdmin) posters else posters.filter { it.active }
                    listContainer.removeAllViews()
                    emptyText.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                    visible.forEach { poster ->
                        listContainer.addView(createPosterRow(poster, onEdit = {
                            editingPoster = poster
                            titleInput.setText(poster.title)
                            contentInput.setText(poster.content)
                            imageInput.setText(poster.imageUrl)
                            roomInput.setText(poster.roomId)
                            activeSwitch.isChecked = poster.active
                            submitButton.text = getString(R.string.poster_update)
                        }, onDelete = {
                            SupabaseRepository.deletePoster(
                                posterId = poster.id,
                                onSuccess = {
                                    toast(getString(R.string.success_poster_deleted))
                                    loadPosters()
                                },
                                onError = { error -> toast(getString(R.string.error_poster_delete, error.message.orEmpty())) }
                            )
                        }))
                    }
                },
                onError = { error -> toast(getString(R.string.error_poster_load, error.message.orEmpty())) }
            )
        }

        loadPosters()

        submitButton.setOnClickListener {
            val title = titleInput.text?.toString().orEmpty().trim()
            val content = contentInput.text?.toString().orEmpty().trim()
            val imageUrl = imageInput.text?.toString().orEmpty().trim()
            val roomCode = roomInput.text?.toString().orEmpty().trim()
            if (title.isBlank() || content.isBlank()) {
                toast(getString(R.string.error_poster_required))
                return@setOnClickListener
            }
            val poster = Poster(
                id = editingPoster?.id.orEmpty(),
                type = "recommend",
                title = title,
                content = content,
                imageUrl = imageUrl,
                roomId = roomCode,
                active = activeSwitch.isChecked,
                role = "client",
                createdAt = editingPoster?.createdAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            SupabaseRepository.createPoster(
                poster = poster,
                onSuccess = {
                    toast(getString(R.string.success_poster_published))
                    editingPoster = null
                    titleInput.setText("")
                    contentInput.setText("")
                    imageInput.setText("")
                    roomInput.setText("")
                    activeSwitch.isChecked = true
                    submitButton.text = getString(R.string.poster_recommend_submit)
                    loadPosters()
                },
                onError = { error ->
                    toast(getString(R.string.error_poster_publish, error.message.orEmpty()))
                }
            )
        }
    }

    private fun createPosterRow(poster: Poster, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_s)
            cardElevation = 0f
            setContentPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            setOnClickListener { openLinkedRoom(poster) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (poster.imageUrl.isNotBlank()) {
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.list_thumb_m) * 2
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(poster.imageUrl) {
                    placeholder(R.mipmap.ic_launcher)
                    error(R.mipmap.ic_launcher)
                    crossfade(true)
                }
            }
            content.addView(image)
        }
        val detail = TextView(this).apply {
            val status = if (poster.active) getString(R.string.poster_status_visible) else getString(R.string.poster_status_hidden)
            text = getString(
                R.string.poster_recommend_item_detail,
                poster.title,
                poster.content,
                poster.roomId.ifBlank { getString(R.string.common_na) },
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
                text = getString(R.string.poster_edit)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onEdit() }
            }
            val deleteButton = MaterialButton(this).apply {
                text = getString(R.string.poster_delete)
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

    private fun openLinkedRoom(poster: Poster) {
        if (poster.roomId.isBlank()) return
        startActivity(
            Intent(this, RoomDetailActivity::class.java)
                .putExtra(RoomDetailActivity.EXTRA_ROOM_CODE, poster.roomId)
        )
    }
}
