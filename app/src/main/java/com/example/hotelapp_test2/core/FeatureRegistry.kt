package com.example.hotelapp_test2.core

import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.ui.features.NotificationsActivity
import com.example.hotelapp_test2.ui.features.ProfileActivity

object FeatureRegistry {
    val items = listOf(
        FeatureItem(
            id = "profile",
            titleRes = R.string.feature_profile_title,
            subtitleRes = R.string.feature_profile_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = ProfileActivity::class.java
        ),
        FeatureItem(
            id = "notifications",
            titleRes = R.string.notifications_title,
            subtitleRes = R.string.toolbar_notifications_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = NotificationsActivity::class.java
        )
    )
}
