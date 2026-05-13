package com.example.hotelapp_test2.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import coil.load
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.R

class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        logo.load("https://jdwgmulywzzqwtrukebf.supabase.co/storage/v1/object/public/rooms/logo.png") {
            crossfade(true)
            placeholder(R.drawable.splash_logo)
            error(R.drawable.splash_logo)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1800)
    }
}
