package com.example.hotelapp_test2.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        val emailInput = view.findViewById<TextInputEditText>(R.id.loginEmail)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.loginPassword)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)
        val googleButton = view.findViewById<MaterialButton>(R.id.googleLoginButton)

        loginButton.setOnClickListener {
            val email = emailInput.text?.toString().orEmpty().trim()
            val password = passwordInput.text?.toString().orEmpty()
            if (email.isBlank() || password.isBlank()) {
                context?.toast(getString(R.string.error_required_email_password))
                return@setOnClickListener
            }

            SupabaseRepository.signInWithEmail(
                email = email,
                password = password,
                onSuccess = { user ->
                    if (!isAdded) return@signInWithEmail
                    val uid = user.uid
                    SupabaseRepository.fetchUserProfile(
                        userId = uid,
                        onSuccess = { profile ->
                            if (!isAdded) return@fetchUserProfile
                            if (profile == null) {
                                val ctx = context ?: return@fetchUserProfile
                                SupabaseRepository.ensureUserProfile(
                                    context = ctx,
                                    name = "",
                                    email = email,
                                    phone = "",
                                    requestedRole = "client",
                                    onSuccess = {
                                        if (!isAdded) return@ensureUserProfile
                                        context?.toast(getString(R.string.success_signin))
                                        startActivity(Intent(ctx, MainActivity::class.java))
                                        activity?.finish()
                                    },
                                    onError = { error ->
                                        if (!isAdded) return@ensureUserProfile
                                        if (error.isProfileWriteForbidden()) {
                                            SessionManager.setUser(ctx, uid, "client")
                                            context?.toast(getString(R.string.success_signin))
                                            startActivity(Intent(ctx, MainActivity::class.java))
                                            activity?.finish()
                                        } else {
                                            context?.toast(getString(R.string.error_create_profile, error.message.orEmpty()))
                                        }
                                    }
                                )
                            } else {
                                context?.let { SessionManager.setUser(it, uid, profile.role) }
                                context?.toast(getString(R.string.success_signin))
                                context?.let { startActivity(Intent(it, MainActivity::class.java)) }
                                activity?.finish()
                            }
                        },
                        onError = { error ->
                            if (!isAdded) return@fetchUserProfile
                            context?.toast(getString(R.string.error_load_profile, error.message.orEmpty()))
                        }
                    )
                },
                onError = { error ->
                    if (!isAdded) return@signInWithEmail
                    context?.toast(getString(R.string.error_signin, error.message.orEmpty()))
                }
            )
        }

        googleButton.setOnClickListener {
            (activity as? AuthActivity)?.launchGoogleSignIn()
        }

        return view
    }
}

private fun Throwable.isProfileWriteForbidden(): Boolean {
    val text = message.orEmpty()
    return text.contains("Upsert users failed: HTTP 403", ignoreCase = true)
}
