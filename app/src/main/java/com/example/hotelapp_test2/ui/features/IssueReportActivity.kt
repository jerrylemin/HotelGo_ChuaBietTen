package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.Booking
import com.example.hotelapp_test2.data.model.IssueReport
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.data.model.Room
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class IssueReportActivity : BaseActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyText: TextView
    private var isAdmin: Boolean = false
    private var selectedBooking: Booking? = null
    private var selectedRoom: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_report)
        setupToolbar(R.string.issue_title, R.string.toolbar_issue_subtitle)
        val role = SessionManager.getRole(this)
        isAdmin = role == "admin"

        val formCard = findViewById<MaterialCardView>(R.id.issueFormCard)
        val bookingContainer = findViewById<LinearLayout>(R.id.issueBookingContainer)
        val selectedBookingText = findViewById<TextView>(R.id.issueSelectedBooking)
        val typeInput = findViewById<TextInputEditText>(R.id.issueType)
        val descriptionInput = findViewById<TextInputEditText>(R.id.issueDescription)
        val submitButton = findViewById<MaterialButton>(R.id.issueSubmitButton)
        listContainer = findViewById(R.id.issueListContainer)
        emptyText = findViewById(R.id.issueEmptyText)

        formCard.visibility = if (isAdmin) View.GONE else View.VISIBLE
        if (!isAdmin) {
            loadReportableBookings(bookingContainer, selectedBookingText)
        }

        submitButton.setOnClickListener {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            val type = typeInput.text?.toString().orEmpty().trim()
            val description = descriptionInput.text?.toString().orEmpty().trim()
            val booking = selectedBooking
            val room = selectedRoom
            if (booking == null || room == null) {
                toast(getString(R.string.issue_select_booking_first))
                return@setOnClickListener
            }
            if (type.isBlank() || description.isBlank()) {
                toast(getString(R.string.error_issue_required))
                return@setOnClickListener
            }
            saveIssue(userId, room.id.ifBlank { room.code.ifBlank { booking.roomId } }, booking, type, description) {
                selectedBooking = null
                selectedRoom = null
                selectedBookingText.text = getString(R.string.issue_select_booking_first)
                typeInput.setText("")
                descriptionInput.setText("")
                loadReportableBookings(bookingContainer, selectedBookingText)
                loadIssues()
            }
        }

        loadIssues()
    }

    private fun loadReportableBookings(container: LinearLayout, selectedBookingText: TextView) {
        val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        if (userId.isBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }
        container.removeAllViews()
        container.addView(simpleText(getString(R.string.issue_booking_loading)))
        SupabaseRepository.listBookings(
            userId = userId,
            onSuccess = { bookings ->
                val reportableBookings = bookings.filter { it.status != "cancelled" }
                if (reportableBookings.isEmpty()) {
                    container.removeAllViews()
                    container.addView(simpleText(getString(R.string.issue_booking_empty)))
                    return@listBookings
                }
                SupabaseRepository.searchRooms(
                    queryText = "",
                    onSuccess = { rooms ->
                        val roomLookup = buildMap {
                            rooms.forEach { room ->
                                if (room.id.isNotBlank()) put(room.id, room)
                                if (room.code.isNotBlank()) put(room.code, room)
                            }
                        }
                        container.removeAllViews()
                        reportableBookings.forEach { booking ->
                            container.addView(createBookingCard(booking, roomLookup[booking.roomId], selectedBookingText))
                        }
                    }
                ) { error ->
                    container.removeAllViews()
                    container.addView(simpleText(getString(R.string.error_room_load, error.message.orEmpty())))
                }
            },
            onError = { error ->
                container.removeAllViews()
                container.addView(simpleText(getString(R.string.error_booking_history, error.message.orEmpty())))
            }
        )
    }

    private fun createBookingCard(booking: Booking, room: Room?, selectedBookingText: TextView): View {
        val card = MaterialCardView(this).apply {
            useCompatPadding = true
            radius = resources.getDimension(R.dimen.radius_s)
            setCardBackgroundColor(getColor(R.color.surface_card))
            setOnClickListener {
                selectedBooking = booking
                selectedRoom = room ?: Room(id = booking.roomId, code = booking.roomId)
                val roomName = roomDisplayName(room, booking)
                selectedBookingText.text = getString(
                    R.string.issue_selected_booking,
                    room?.hotelName?.ifBlank { getString(R.string.common_na) } ?: getString(R.string.common_na),
                    roomName,
                    booking.id
                )
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s),
                resources.getDimensionPixelSize(R.dimen.space_s)
            )
        }
        val image = ImageView(this).apply {
            val size = resources.getDimensionPixelSize(R.dimen.list_thumb_m)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(room?.images?.firstOrNull().orEmpty().ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
        }
        val detail = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = resources.getDimensionPixelSize(R.dimen.space_s)
            }
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            text = getString(
                R.string.issue_booking_item,
                room?.hotelName?.ifBlank { getString(R.string.common_na) } ?: getString(R.string.common_na),
                roomDisplayName(room, booking),
                booking.checkIn,
                booking.checkOut,
                booking.id,
                bookingStatusLabel(booking.status)
            )
        }
        row.addView(image)
        row.addView(detail)
        card.addView(row)
        return card
    }

    private fun roomDisplayName(room: Room?, booking: Booking): String {
        return room?.displayType?.ifBlank { room.type }?.ifBlank { room.code } ?: booking.roomId
    }

    private fun simpleText(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(getColor(R.color.text_secondary))
        textSize = 13f
    }

    private fun saveIssue(userId: String, roomId: String, booking: Booking, type: String, description: String, onSaved: () -> Unit) {
        val issue = IssueReport(
            userId = userId,
            roomId = roomId,
            bookingId = booking.id,
            title = type,
            description = description,
            status = "new",
            createdAt = System.currentTimeMillis()
        )
        SupabaseRepository.createIssue(
            issue = issue,
            onSuccess = {
                toast(getString(R.string.success_issue_sent))
                showCompletionPopup(
                    NotificationSettings.CATEGORY_ISSUE,
                    R.string.completion_title,
                    R.string.completion_issue_sent
                )
                SupabaseRepository.createNotification(
                    AppNotification(
                        title = getString(R.string.issue_notification_title),
                        body = getString(R.string.issue_notification_body, roomId),
                        targetRole = "admin"
                    ),
                    onSuccess = {},
                    onError = {}
                )
                onSaved()
            },
            onError = { error ->
                toast(getString(R.string.error_issue_send, error.message.orEmpty()))
            }
        )
    }

    private fun loadIssues() {
        val userId = if (isAdmin) null else SupabaseRepository.currentUser()?.uid.orEmpty()
        if (!isAdmin && userId.isNullOrBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }
        SupabaseRepository.listIssues(
            userId = userId,
            onSuccess = { issues ->
                renderIssues(issues)
            },
            onError = { error ->
                toast(getString(R.string.error_issue_load, error.message.orEmpty()))
            }
        )
    }

    private fun renderIssues(issues: List<IssueReport>) {
        listContainer.removeAllViews()
        emptyText.visibility = if (issues.isEmpty()) View.VISIBLE else View.GONE
        issues.forEach { issue ->
            val card = MaterialCardView(this).apply {
                radius = resources.getDimension(com.example.hotelapp_test2.R.dimen.radius_m)
                cardElevation = 0f
                setContentPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val detail = TextView(this).apply {
                val clientLabel = clientLabel(issue.userName, issue.userPhone, issue.userId)
                text = getString(
                    R.string.issue_list_item,
                    clientLabel,
                    issue.roomId.ifBlank { getString(R.string.common_na) },
                    issue.bookingId.ifBlank { getString(R.string.common_na) },
                    issue.title,
                    issue.description,
                    issueStatusLabel(issue.status)
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
                listOf(
                    "new" to R.string.issue_status_new,
                    "processing" to R.string.issue_status_processing,
                    "resolved" to R.string.issue_status_resolved
                ).forEach { (status, labelRes) ->
                    val button = MaterialButton(this).apply {
                        text = getString(labelRes)
                        isEnabled = issue.status != status
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    button.setOnClickListener { updateIssue(issue.id, status) }
                    actions.addView(button)
                }
                content.addView(actions)
            }
            card.addView(content)
            listContainer.addView(card)
        }
    }

    private fun updateIssue(issueId: String, status: String) {
        SupabaseRepository.updateIssueStatus(
            issueId = issueId,
            status = status,
            onSuccess = {
                toast(getString(R.string.success_issue_updated))
                SupabaseRepository.createNotification(
                    AppNotification(
                        title = getString(R.string.issue_response_notification_title),
                        body = getString(R.string.issue_response_notification_body),
                        targetRole = "client"
                    ),
                    onSuccess = {},
                    onError = {}
                )
                loadIssues()
            },
            onError = { error ->
                toast(getString(R.string.error_issue_update, error.message.orEmpty()))
            }
        )
    }

    private fun issueStatusLabel(status: String): String = when (status) {
        "processing" -> getString(R.string.issue_status_processing)
        "resolved" -> getString(R.string.issue_status_resolved)
        else -> getString(R.string.issue_status_new)
    }

    private fun clientLabel(name: String, phone: String, userId: String): String {
        val displayName = name.ifBlank { userId.takeLast(6).ifBlank { getString(R.string.common_na) } }
        val displayPhone = phone.ifBlank { getString(R.string.common_na) }
        return getString(R.string.client_contact_format, displayName, displayPhone)
    }

    private fun bookingStatusLabel(status: String): String = when (status) {
        "pending" -> getString(R.string.booking_status_pending)
        "confirmed" -> getString(R.string.booking_status_confirmed)
        "paid" -> getString(R.string.booking_status_paid)
        "checked_in" -> getString(R.string.booking_status_checked_in)
        "checked_out" -> getString(R.string.booking_status_checked_out)
        "completed" -> getString(R.string.booking_status_completed)
        "cancelled" -> getString(R.string.booking_status_cancelled)
        else -> status
    }
}
