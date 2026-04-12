package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import android.widget.TextView
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.model.NotificationSettings
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setupToolbar(R.string.notifications_title, R.string.toolbar_notifications_subtitle)

        val checkInSwitch = findViewById<SwitchMaterial>(R.id.notifCheckInSwitch)
        val promoSwitch = findViewById<SwitchMaterial>(R.id.notifPromoSwitch)
        val roomSwitch = findViewById<SwitchMaterial>(R.id.notifRoomSwitch)
        val listText = findViewById<TextView>(R.id.notificationListText)

        val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        if (userId.isBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }

        SupabaseRepository.fetchNotificationSettings(
            userId = userId,
            onSuccess = { settings ->
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
                roomStatus = roomSwitch.isChecked
            )
            SupabaseRepository.updateNotificationSettings(
                userId = userId,
                settings = settings,
                onSuccess = {},
                onError = { error -> toast(getString(R.string.error_notifications_save, error.message.orEmpty())) }
            )
        }

        checkInSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
        promoSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
        roomSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }

        val role = SessionManager.getRole(this)
        SupabaseRepository.listenNotifications(
            role = role,
            onSuccess = { notifications ->
                listText.text = if (notifications.isEmpty()) {
                    getString(R.string.notifications_empty)
                } else {
                    notifications.joinToString("\n") { getString(R.string.notification_list_item, it.title, it.body) }
                }
            },
            onError = { error -> toast(getString(R.string.error_notifications_load, error.message.orEmpty())) }
        )
    }
}
