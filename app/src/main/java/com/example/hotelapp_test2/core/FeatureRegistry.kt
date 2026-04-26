package com.example.hotelapp_test2.core

import com.example.hotelapp_test2.ui.features.AddOnItemsActivity
import com.example.hotelapp_test2.ui.features.BookingHistoryActivity
import com.example.hotelapp_test2.ui.features.CheckInOutActivity
import com.example.hotelapp_test2.ui.features.HotelSearchActivity
import com.example.hotelapp_test2.ui.features.IssueReportActivity
import com.example.hotelapp_test2.ui.features.PaymentActivity
import com.example.hotelapp_test2.ui.features.ProfileActivity
import com.example.hotelapp_test2.ui.features.RecommendationPosterActivity
import com.example.hotelapp_test2.ui.features.ReviewActivity
import com.example.hotelapp_test2.ui.features.RoomCrudActivity
import com.example.hotelapp_test2.ui.features.RoomSearchActivity
import com.example.hotelapp_test2.ui.features.SearchPosterActivity
import com.example.hotelapp_test2.ui.features.VoucherActivity
import com.example.hotelapp_test2.R

object FeatureRegistry {
    val items = listOf(
        FeatureItem(
            id = "hotel_catalog",
            titleRes = R.string.feature_hotel_catalog_title,
            subtitleRes = R.string.feature_hotel_catalog_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = HotelSearchActivity::class.java
        ),
        FeatureItem(
            id = "room_search",
            titleRes = R.string.feature_room_search_title,
            subtitleRes = R.string.feature_room_search_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = RoomSearchActivity::class.java
        ),
        FeatureItem(
            id = "room_crud",
            titleRes = R.string.feature_room_crud_title,
            subtitleRes = R.string.feature_room_crud_subtitle,
            roles = setOf(FeatureRole.ADMIN),
            activityClass = RoomCrudActivity::class.java
        ),
        FeatureItem(
            id = "review",
            titleRes = R.string.feature_review_title,
            subtitleRes = R.string.feature_review_subtitle,
            roles = setOf(FeatureRole.CLIENT),
            activityClass = ReviewActivity::class.java
        ),
        FeatureItem(
            id = "issue",
            titleRes = R.string.feature_issue_title,
            subtitleRes = R.string.feature_issue_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = IssueReportActivity::class.java
        ),
        FeatureItem(
            id = "payment",
            titleRes = R.string.feature_payment_title,
            subtitleRes = R.string.feature_payment_subtitle,
            roles = setOf(FeatureRole.CLIENT),
            activityClass = PaymentActivity::class.java
        ),
        FeatureItem(
            id = "addon",
            titleRes = R.string.feature_addon_title,
            subtitleRes = R.string.feature_addon_subtitle,
            roles = setOf(FeatureRole.ADMIN),
            activityClass = AddOnItemsActivity::class.java
        ),
        FeatureItem(
            id = "voucher",
            titleRes = R.string.feature_voucher_title,
            subtitleRes = R.string.feature_voucher_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = VoucherActivity::class.java
        ),
        FeatureItem(
            id = "poster_recommend",
            titleRes = R.string.feature_poster_recommend_title,
            subtitleRes = R.string.feature_poster_recommend_subtitle,
            roles = setOf(FeatureRole.ADMIN),
            activityClass = RecommendationPosterActivity::class.java
        ),
        FeatureItem(
            id = "poster_search",
            titleRes = R.string.feature_poster_search_title,
            subtitleRes = R.string.feature_poster_search_subtitle,
            roles = setOf(FeatureRole.CLIENT),
            activityClass = SearchPosterActivity::class.java
        ),
        FeatureItem(
            id = "profile",
            titleRes = R.string.feature_profile_title,
            subtitleRes = R.string.feature_profile_subtitle,
            roles = setOf(FeatureRole.ADMIN, FeatureRole.CLIENT),
            activityClass = ProfileActivity::class.java
        ),
        FeatureItem(
            id = "booking_history_admin",
            titleRes = R.string.feature_booking_history_admin_title,
            subtitleRes = R.string.feature_booking_history_admin_subtitle,
            roles = setOf(FeatureRole.ADMIN),
            activityClass = BookingHistoryActivity::class.java
        ),
        FeatureItem(
            id = "booking_manage_client",
            titleRes = R.string.feature_booking_history_client_title,
            subtitleRes = R.string.feature_booking_history_client_subtitle,
            roles = setOf(FeatureRole.CLIENT),
            activityClass = BookingHistoryActivity::class.java
        ),
        FeatureItem(
            id = "checkin",
            titleRes = R.string.feature_checkin_title,
            subtitleRes = R.string.feature_checkin_subtitle,
            roles = setOf(FeatureRole.ADMIN),
            activityClass = CheckInOutActivity::class.java
        )
    )
}


