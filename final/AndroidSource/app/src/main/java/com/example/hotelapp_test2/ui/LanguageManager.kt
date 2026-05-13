package com.example.hotelapp_test2.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "hotelgo_language"
    private const val KEY_LANGUAGE = "language"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_VI = "vi"

    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_VI)
            ?.takeIf { it == LANGUAGE_EN || it == LANGUAGE_VI }
            ?: LANGUAGE_VI
    }

    fun nextLanguage(context: Context): String {
        return if (getLanguage(context) == LANGUAGE_VI) LANGUAGE_EN else LANGUAGE_VI
    }

    fun toggle(activity: Activity) {
        val next = nextLanguage(activity)
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, next)
            .apply()
        activity.recreate()
    }
}
