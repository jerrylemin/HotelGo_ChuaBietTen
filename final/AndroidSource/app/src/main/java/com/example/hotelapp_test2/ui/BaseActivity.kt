package com.example.hotelapp_test2.ui

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.features.NotificationsActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    protected fun setupToolbar(
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int? = null,
        showBack: Boolean = true,
        showLanguageToggle: Boolean = true
    ) {
        setupToolbar(
            title = getString(titleRes),
            subtitle = subtitleRes?.let { getString(it) },
            showBack = showBack,
            showLanguageToggle = showLanguageToggle
        )
    }

    protected fun setupToolbar(
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true,
        showLanguageToggle: Boolean = true
    ) {
        val toolbar = findViewById<MaterialToolbar?>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
            supportActionBar?.title = title
            supportActionBar?.subtitle = subtitle
            if (showBack) {
                toolbar.setNavigationOnClickListener {
                    onBackPressedDispatcher.onBackPressed()
                }
            } else {
                toolbar.navigationIcon = null
                toolbar.setNavigationOnClickListener(null)
            }
            if (showLanguageToggle) {
                setupLanguageToggle(toolbar)
            }
        } else {
            supportActionBar?.title = title
            supportActionBar?.subtitle = subtitle
        }
    }

    protected fun requireRole(requiredRole: String, message: String = getString(R.string.error_no_access)): Boolean {
        val role = SessionManager.getRole(this)
        if (role != requiredRole) {
            toast(message)
            finish()
            return false
        }
        return true
    }

    protected fun showCompletionPopup(
        category: String,
        title: String,
        message: String,
        fallbackToast: Boolean = true,
        onDismiss: (() -> Unit)? = null
    ) {
        val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        if (userId.isBlank()) {
            if (fallbackToast) toast(message)
            onDismiss?.invoke()
            return
        }
        SupabaseRepository.fetchNotificationSettings(
            userId = userId,
            onSuccess = { settings ->
                if (!settings.enabledFor(category) || isFinishing || isDestroyed) {
                    onDismiss?.invoke()
                    return@fetchNotificationSettings
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.common_ok) { _, _ -> onDismiss?.invoke() }
                    .setNegativeButton(R.string.notification_settings_action) { _, _ ->
                        startActivity(Intent(this, NotificationsActivity::class.java))
                        onDismiss?.invoke()
                    }
                    .setOnCancelListener { onDismiss?.invoke() }
                    .show()
            },
            onError = {
                if (fallbackToast) toast(message)
                onDismiss?.invoke()
            }
        )
    }

    protected fun showCompletionPopup(
        category: String,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) {
        showCompletionPopup(category, getString(titleRes), getString(messageRes))
    }

    private fun setupLanguageToggle(toolbar: MaterialToolbar) {
        val title = getString(
            if (LanguageManager.nextLanguage(this) == LanguageManager.LANGUAGE_VI) {
                R.string.language_toggle_vi
            } else {
                R.string.language_toggle_en
            }
        )

        toolbar.menu.removeItem(MENU_LANGUAGE_ID)
        val button = toolbar.findViewWithTag<MaterialButton>(LANGUAGE_TOGGLE_TAG)
            ?: MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).also { newButton ->
                newButton.tag = LANGUAGE_TOGGLE_TAG
                newButton.minWidth = 0
                newButton.minimumWidth = 0
                newButton.layoutParams = Toolbar.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
                toolbar.addView(newButton)
            }
        button.text = title
        button.contentDescription = getString(R.string.language_toggle_content_description)
        button.setOnClickListener {
            LanguageManager.toggle(this)
        }
    }

    private companion object {
        const val MENU_LANGUAGE_ID = 1001
        const val LANGUAGE_TOGGLE_TAG = "hotelgo_language_toggle"
    }
}
