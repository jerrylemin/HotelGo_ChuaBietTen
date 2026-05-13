package com.example.hotelapp_test2.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RegisterFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        val nameInput = view.findViewById<TextInputEditText>(R.id.registerName)
        val phoneInput = view.findViewById<TextInputEditText>(R.id.registerPhone)
        val emailInput = view.findViewById<TextInputEditText>(R.id.registerEmail)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.registerPassword)
        val confirmInput = view.findViewById<TextInputEditText>(R.id.registerConfirm)
        val registerButton = view.findViewById<MaterialButton>(R.id.registerButton)

        registerButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val phone = phoneInput.text?.toString().orEmpty().trim()
            val email = emailInput.text?.toString().orEmpty().trim()
            val password = passwordInput.text?.toString().orEmpty()
            val confirm = confirmInput.text?.toString().orEmpty()
            if (email.isBlank() || password.isBlank() || confirm.isBlank()) {
                context?.toast(getString(R.string.error_required_full_info))
                return@setOnClickListener
            }
            if (password != confirm) {
                context?.toast(getString(R.string.error_password_mismatch))
                return@setOnClickListener
            }

            SupabaseRepository.signUpWithEmail(
                email = email,
                password = password,
                name = name,
                phone = phone,
                onSuccess = { result ->
                    if (!isAdded) return@signUpWithEmail
                    if (result.requiresEmailConfirmation) {
                        context?.toast(getString(R.string.success_signup_check_email))
                        passwordInput.setText("")
                        confirmInput.setText("")
                        return@signUpWithEmail
                    }

                    val ctx = context ?: return@signUpWithEmail
                    SupabaseRepository.ensureUserProfile(
                        context = ctx,
                        name = name,
                        email = email,
                        phone = phone,
                        requestedRole = "client",
                        onSuccess = {
                            if (!isAdded) return@ensureUserProfile
                            context?.toast(getString(R.string.success_signup))
                            startActivity(Intent(ctx, MainActivity::class.java))
                            activity?.finish()
                        },
                        onError = { error ->
                            if (!isAdded) return@ensureUserProfile
                            context?.toast(getString(R.string.error_create_profile, error.message.orEmpty()))
                        }
                    )
                },
                onError = { error ->
                    if (!isAdded) return@signUpWithEmail
                    context?.toast(getString(R.string.error_signup, error.message.orEmpty()))
                }
            )
        }

        return view
    }
}
