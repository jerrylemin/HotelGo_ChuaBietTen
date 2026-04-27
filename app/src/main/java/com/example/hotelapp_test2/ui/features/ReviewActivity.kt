package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.data.model.Review
import com.example.hotelapp_test2.data.model.ReviewableBooking
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReviewActivity : BaseActivity() {
    private var selectedBooking: ReviewableBooking? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)
        setupToolbar(R.string.feature_review_title, R.string.toolbar_review_subtitle)
        if (!requireRole("client")) return

        val bookingContainer = findViewById<LinearLayout>(R.id.reviewBookingContainer)
        val selectedText = findViewById<TextView>(R.id.reviewSelectedBooking)
        val ratingBar = findViewById<RatingBar>(R.id.reviewRating)
        val commentInput = findViewById<TextInputEditText>(R.id.reviewComment)
        val submitButton = findViewById<MaterialButton>(R.id.reviewSubmitButton)
        val recentText = findViewById<TextView>(R.id.reviewRecentText)

        fun loadReviewableBookings() {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return
            }
            SupabaseRepository.listReviewableBookings(
                userId = userId,
                onSuccess = { items ->
                    bookingContainer.removeAllViews()
                    selectedBooking = null
                    selectedText.text = getString(R.string.review_select_booking_first)
                    submitButton.isEnabled = false
                    if (items.isEmpty()) {
                        bookingContainer.addView(simpleText(getString(R.string.review_booking_empty)))
                    } else {
                        items.forEach { item ->
                            bookingContainer.addView(createBookingCard(item, selectedText, ratingBar, commentInput, submitButton))
                        }
                    }
                },
                onError = { error -> toast(getString(R.string.error_review_load, error.message.orEmpty())) }
            )
        }

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
                onError = { error -> toast(getString(R.string.error_review_load, error.message.orEmpty())) }
            )
        }

        loadReviewableBookings()
        loadRecent()

        submitButton.setOnClickListener {
            val selection = selectedBooking
            val booking = selection?.booking
            val room = selection?.room
            val rating = ratingBar.rating.toInt().coerceIn(1, 5)
            val comment = commentInput.text?.toString().orEmpty().trim()
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            if (selection == null || booking == null || room == null) {
                toast(getString(R.string.review_select_booking_first))
                return@setOnClickListener
            }
            if (selection.existingReview != null) {
                toast(getString(R.string.review_already_done))
                return@setOnClickListener
            }
            if (rating == 0 || comment.isBlank()) {
                toast(getString(R.string.error_review_required))
                return@setOnClickListener
            }

            val review = Review(
                roomId = booking.roomId,
                hotelId = room.hotelId,
                bookingId = booking.id,
                userId = userId,
                rating = rating,
                comment = comment,
                createdAt = System.currentTimeMillis()
            )
            SupabaseRepository.createReviewAndRefreshRoom(
                review = review,
                onSuccess = {
                    toast(getString(R.string.success_review_sent))
                    showCompletionPopup(
                        NotificationSettings.CATEGORY_REVIEW,
                        R.string.completion_title,
                        R.string.completion_review_sent
                    )
                    commentInput.setText("")
                    ratingBar.rating = 0f
                    loadReviewableBookings()
                    loadRecent()
                },
                onError = { error -> toast(getString(R.string.error_review_send, error.message.orEmpty())) }
            )
        }
    }

    private fun createBookingCard(
        item: ReviewableBooking,
        selectedText: TextView,
        ratingBar: RatingBar,
        commentInput: TextInputEditText,
        submitButton: MaterialButton
    ): View {
        val context = this
        val room = item.room
        val booking = item.booking
        val card = MaterialCardView(context).apply {
            useCompatPadding = true
            radius = resources.getDimension(R.dimen.radius_s)
            setCardBackgroundColor(getColor(R.color.surface_card))
            setOnClickListener {
                selectedBooking = item
                selectedText.text = getString(
                    R.string.review_selected_booking,
                    compactHotelName(room?.hotelName.orEmpty()),
                    compactRoomName(room?.displayType?.ifBlank { room.type }.orEmpty().ifBlank { booking.roomId }),
                    shortId(booking.id)
                )
                item.existingReview?.let { review ->
                    ratingBar.rating = review.rating.coerceIn(1, 5).toFloat()
                    commentInput.setText(review.comment)
                } ?: run {
                    ratingBar.rating = 0f
                    commentInput.setText("")
                }
                submitButton.isEnabled = item.existingReview == null
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s)
            )
        }
        val image = ImageView(context).apply {
            val size = resources.getDimensionPixelSize(R.dimen.list_thumb_m)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(room?.images?.firstOrNull().orEmpty().ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
        }
        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = resources.getDimensionPixelSize(R.dimen.space_s)
            }
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            val reviewed = if (item.existingReview == null) getString(R.string.review_can_review) else getString(R.string.review_already_done)
            text = getString(
                R.string.review_booking_item,
                compactHotelName(room?.hotelName.orEmpty()),
                compactRoomName(room?.displayType?.ifBlank { room.type }.orEmpty().ifBlank { booking.roomId }),
                shortDate(booking.checkIn),
                shortDate(booking.checkOut),
                "#${shortId(booking.id)}",
                statusLabel(booking.status),
                reviewed
            )
        }
        row.addView(image)
        row.addView(text)
        card.addView(row)
        return card
    }

    private fun simpleText(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(getColor(R.color.text_secondary))
        textSize = 13f
    }

    private fun statusLabel(status: String): String = when (status) {
        "completed" -> getString(R.string.booking_status_completed)
        "confirmed" -> getString(R.string.booking_status_confirmed)
        "paid" -> getString(R.string.booking_status_paid)
        "checked_out" -> getString(R.string.booking_status_checked_out)
        else -> status
    }

    private fun compactHotelName(value: String): String =
        value.ifBlank { getString(R.string.common_na) }.toDisplayWords(maxWords = 4)

    private fun compactRoomName(value: String): String =
        value.ifBlank { getString(R.string.common_na) }.toDisplayWords(maxWords = 3)

    private fun shortDate(value: String): String =
        runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM"))
        }.getOrDefault(value)

    private fun shortId(value: String): String {
        val clean = value.trim()
        if (clean.length <= 8) return clean
        return clean.takeLast(6)
    }

    private fun String.toDisplayWords(maxWords: Int): String =
        replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(maxWords)
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
}
