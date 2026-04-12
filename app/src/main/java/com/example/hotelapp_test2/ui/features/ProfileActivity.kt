package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.UserProfile
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ProfileActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setupToolbar(R.string.profile_title, R.string.toolbar_profile_subtitle)

        val nameInput = findViewById<TextInputEditText>(R.id.profileName)
        val emailInput = findViewById<TextInputEditText>(R.id.profileEmail)
        val phoneInput = findViewById<TextInputEditText>(R.id.profilePhone)
        val saveButton = findViewById<MaterialButton>(R.id.profileSaveButton)

        val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
        if (userId.isBlank()) {
            toast(getString(R.string.error_login_required))
            return
        }
        var currentRole = "client"

        SupabaseRepository.fetchUserProfile(
            userId = userId,
            onSuccess = { profile ->
                if (profile != null) {
                    nameInput.setText(profile.name)
                    emailInput.setText(profile.email)
                    phoneInput.setText(profile.phone)
                    currentRole = profile.role
                }
            },
            onError = { error ->
                toast(getString(R.string.error_load_profile, error.message.orEmpty()))
            }
        )

        saveButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val email = emailInput.text?.toString().orEmpty().trim()
            val phone = phoneInput.text?.toString().orEmpty().trim()
            val profile = UserProfile(
                id = userId,
                name = name,
                email = email,
                phone = phone,
                role = currentRole
            )
            SupabaseRepository.updateUserProfile(
                profile = profile,
                onSuccess = { toast(getString(R.string.success_profile_updated)) },
                onError = { error -> toast(getString(R.string.error_profile_update, error.message.orEmpty())) }
            )
        }
    }
}
