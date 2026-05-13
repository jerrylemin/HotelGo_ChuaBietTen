package com.example.hotelapp_test2

import android.app.Application
import com.example.hotelapp_test2.data.SupabaseRepository

class HotelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseRepository.init(this)
    }
}
