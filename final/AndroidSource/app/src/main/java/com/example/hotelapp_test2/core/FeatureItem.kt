package com.example.hotelapp_test2.core

import android.app.Activity
import androidx.annotation.StringRes

data class FeatureItem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val roles: Set<FeatureRole>,
    val activityClass: Class<out Activity>
)


