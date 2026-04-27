package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.AppNotification
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationsActivity : BaseActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var unreadText: TextView
    private lateinit var markAllReadButton: MaterialButton
    private var role: String = "client"
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setupToolbar(R.string.notifications_title, R.string.toolbar_notifications_subtitle)

        val bookingSwitch = findViewById<SwitchMaterial>(R.id.notifBookingSwitch)
        val reviewSwitch = findViewById<SwitchMaterial>(R.id.notifReviewSwitch)
        val issueSwitch = findViewById<SwitchMaterial>(R.id.notifIssueSwitch)
        val paymentSwitch = findViewById<SwitchMaterial>(R.id.notifPaymentSwitch)
        val checkInSwitch = findViewById<SwitchMaterial>(R.id.notifCheckInSwitch)
        val roomSwitch = findViewById<SwitchMaterial>(R.id.notifRoomSwitch)
        val promoSwitch = findViewById<SwitchMaterial>(R.id.notifPromoSwitch)
        listContainer = findViewById(R.id.notificationListContainer)
        emptyText = findViewById(R.id.notificationEmptyText)
        unreadText = findViewById(R.id.notificationUnreadText)
        markAllReadButton = findViewById(R.id.notificationMarkAllReadButton)

        userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        if (userId.isBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }

        role = SessionManager.getRole(this)
        SupabaseRepository.fetchNotificationSettings(
            userId = userId,
            onSuccess = { settings ->
                bookingSwitch.isChecked = settings.booking
                reviewSwitch.isChecked = settings.review
                issueSwitch.isChecked = settings.issue
                paymentSwitch.isChecked = settings.payment
                checkInSwitch.isChecked = settings.checkIn
                promoSwitch.isChecked = settings.promo
                roomSwitch.isChecked = settings.roomStatus
            },
            onError = { error -> toast(getString(R.string.error_notifications_settings, error.message.orEmpty())) }
        )

        fun saveSettings() {
            val settings = NotificationSettings(
                checkIn = checkInSwitch.isChecked,
                promo = promoSwitch.isChecked,
                roomStatus = roomSwitch.isChecked,
                booking = bookingSwitch.isChecked,
                review = reviewSwitch.isChecked,
                issue = issueSwitch.isChecked,
                payment = paymentSwitch.isChecked
            )
            SupabaseRepository.updateNotificationSettings(
                userId = userId,
                settings = settings,
                onSuccess = {},
                onError = { error -> toast(getString(R.string.error_notifications_save, error.message.orEmpty())) }
            )
        }

        listOf(
            bookingSwitch,
            reviewSwitch,
            issueSwitch,
            paymentSwitch,
            checkInSwitch,
            roomSwitch,
            promoSwitch
        ).forEach { switch ->
            switch.setOnCheckedChangeListener { _, _ -> saveSettings() }
        }
        markAllReadButton.setOnClickListener {
            SupabaseRepository.markAllNotificationsRead(
                userId = userId,
                role = role,
                onSuccess = { loadNotifications() },
                onError = { error -> toast(getString(R.string.error_notifications_save, error.message.orEmpty())) }
            )
        }
        loadNotifications()
    }

    private fun loadNotifications() {
        SupabaseRepository.listenNotifications(
            role = role,
            userId = userId,
            onSuccess = { notifications ->
                renderNotifications(notifications)
            },
            onError = { error -> toast(getString(R.string.error_notifications_load, error.message.orEmpty())) }
        )
    }

    private fun renderNotifications(notifications: List<AppNotification>) {
        listContainer.removeAllViews()
        val unreadCount = notifications.count { !it.read }
        unreadText.text = getString(R.string.notifications_unread_count, unreadCount)
        emptyText.visibility = if (notifications.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        notifications.forEach { notification ->
            listContainer.addView(createNotificationRow(notification))
        }
    }

    private fun createNotificationRow(notification: AppNotification): MaterialCardView {
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
            val state = if (notification.read) getString(R.string.notifications_read) else getString(R.string.notifications_unread)
            text = getString(R.string.notification_list_item_status, notification.title, notification.body, state)
            setTextColor(getColor(if (notification.read) R.color.text_secondary else R.color.text_primary))
            textSize = 14f
        }
        val button = MaterialButton(this).apply {
            text = getString(if (notification.read) R.string.notifications_mark_unread else R.string.notifications_mark_read)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            setOnClickListener {
                SupabaseRepository.markNotificationRead(
                    notificationId = notification.id,
                    read = !notification.read,
                    onSuccess = { loadNotifications() },
                    onError = { error -> toast(getString(R.string.error_notifications_save, error.message.orEmpty())) }
                )
            }
        }
        content.addView(detail)
        content.addView(button)
        card.addView(content)
        return card
    }
}
