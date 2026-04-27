package com.example.hotelapp_test2.ui.features

import android.content.Intent
import android.os.Bundle
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.ui.BaseActivity

/**
 * Legacy activity kept for backward compatibility with any deep-link returnUrl.
 * Since PayOS is no longer used, this simply redirects to the main screen.
 */
class PayOSReturnActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
