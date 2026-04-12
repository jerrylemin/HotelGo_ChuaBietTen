package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.RatingBar
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.Review
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ReviewActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)
        setupToolbar(R.string.feature_review_title, R.string.toolbar_review_subtitle)
        if (!requireRole("client")) return

        val roomCodeInput = findViewById<TextInputEditText>(R.id.reviewRoomCode)
        val ratingBar = findViewById<RatingBar>(R.id.reviewRating)
        val commentInput = findViewById<TextInputEditText>(R.id.reviewComment)
        val submitButton = findViewById<MaterialButton>(R.id.reviewSubmitButton)
        val recentText = findViewById<TextView>(R.id.reviewRecentText)

        fun loadRecent() {
            SupabaseRepository.listRecentReviews(
                limit = 5,
                onSuccess = { reviews ->
                    recentText.text = if (reviews.isEmpty()) {
                        getString(R.string.review_empty)
                    } else {
                        reviews.joinToString("\n") { getString(R.string.review_list_item, it.roomId, it.comment) }
                    }
                },
                onError = { error ->
                    toast(getString(R.string.error_review_load, error.message.orEmpty()))
                }
            )
        }

        loadRecent()

        submitButton.setOnClickListener {
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            val rating = ratingBar.rating.toInt()
            val comment = commentInput.text?.toString().orEmpty().trim()
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            if (roomCode.isBlank() || rating == 0 || comment.isBlank()) {
                toast(getString(R.string.error_review_required))
                return@setOnClickListener
            }
            val review = Review(
                roomId = roomCode,
                userId = userId,
                rating = rating,
                comment = comment
            )
            SupabaseRepository.createReview(
                review = review,
                onSuccess = {
                    toast(getString(R.string.success_review_sent))
                    commentInput.setText("")
                    loadRecent()
                },
                onError = { error ->
                    toast(getString(R.string.error_review_send, error.message.orEmpty()))
                }
            )
        }
    }
}
