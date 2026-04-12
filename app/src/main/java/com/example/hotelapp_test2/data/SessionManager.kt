package com.example.hotelapp_test2.data

import android.content.Context

object SessionManager {
    private const val PREFS_NAME = "hotel_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ROLE = "role"

    fun setUser(context: Context, userId: String, role: String) {
        val normalizedRole = normalizeRole(role)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE, normalizedRole)
            .apply()
    }

    fun getUserId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null)
    }

    fun getRole(context: Context): String {
        val role = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, "client") ?: "client"
        return normalizeRole(role)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ROLE)
            .apply()
    }

    private fun normalizeRole(role: String): String {
        val value = role.trim().lowercase()
        return if (value == "admin") "admin" else "client"
    }
}
