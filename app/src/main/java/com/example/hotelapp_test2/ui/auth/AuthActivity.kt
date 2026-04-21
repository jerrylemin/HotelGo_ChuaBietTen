package com.example.hotelapp_test2.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.viewpager2.widget.ViewPager2
import com.example.hotelapp_test2.BuildConfig
import com.example.hotelapp_test2.MainActivity
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AuthActivity : BaseActivity() {
    private val credentialManager by lazy { CredentialManager.create(this) }
    private val credentialExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var didRetryAfterClearingState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        setupToolbar(R.string.auth_toolbar_title)

        val tabLayout = findViewById<TabLayout>(R.id.authTabs)
        val viewPager = findViewById<ViewPager2>(R.id.authPager)
        viewPager.adapter = AuthPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(if (position == 0) R.string.auth_tab_login else R.string.auth_tab_register)
        }.attach()
    }

    fun launchGoogleSignIn() {
        didRetryAfterClearingState = false
        launchGoogleSignInInternal()
    }

    private fun launchGoogleSignInInternal() {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            toast(getString(R.string.error_google_client_missing))
            return
        }

        val rawNonce = UUID.randomUUID().toString()
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(rawNonce.sha256())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        credentialManager.getCredentialAsync(
            context = this,
            request = request,
            cancellationSignal = null,
            executor = credentialExecutor,
            callback = object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    handleGoogleCredential(result, rawNonce)
                }

                override fun onError(e: GetCredentialException) {
                    if (shouldRetryAfterReauthFailure(e)) {
                        didRetryAfterClearingState = true
                        GoogleCredentialStateManager.clear(this@AuthActivity) {
                            if (!isFinishing && !isDestroyed) {
                                launchGoogleSignInInternal()
                            }
                        }
                        return
                    }

                    runOnUiThread {
                        val messageRes = if (e is NoCredentialException) {
                            R.string.error_google_no_credentials
                        } else {
                            null
                        }
                        toast(
                            messageRes?.let(::getString)
                                ?: getString(R.string.error_google_account, e.message ?: e.type)
                        )
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        credentialExecutor.shutdown()
    }

    private fun handleGoogleCredential(result: GetCredentialResponse, rawNonce: String) {
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            runOnUiThread { toast(getString(R.string.error_google_credential_invalid)) }
            return
        }

        try {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            signInToSupabase(
                idToken = googleCredential.idToken,
                nonce = rawNonce,
                fallbackEmail = googleCredential.id,
                fallbackName = googleCredential.displayName.orEmpty()
            )
        } catch (e: GoogleIdTokenParsingException) {
            runOnUiThread { toast(getString(R.string.error_google_token, e.message.orEmpty())) }
        }
    }

    private fun signInToSupabase(
        idToken: String,
        nonce: String,
        fallbackEmail: String,
        fallbackName: String
    ) {
        SupabaseRepository.signInWithGoogleIdToken(
            idToken = idToken,
            nonce = nonce,
            onSuccess = { user ->
                if (isFinishing || isDestroyed) return@signInWithGoogleIdToken
                finishGoogleSignIn(
                    user = user,
                    fallbackEmail = fallbackEmail,
                    fallbackName = fallbackName
                )
            },
            onError = { error ->
                if (isFinishing || isDestroyed) return@signInWithGoogleIdToken
                toast(getString(R.string.error_google_signin, error.message.orEmpty()))
            }
        )
    }

    private fun finishGoogleSignIn(
        user: SupabaseRepository.SupabaseUser,
        fallbackEmail: String,
        fallbackName: String
    ) {
        SupabaseRepository.fetchUserProfile(
            userId = user.uid,
            onSuccess = { profile ->
                if (isFinishing || isDestroyed) return@fetchUserProfile
                if (profile == null) {
                    SupabaseRepository.ensureUserProfile(
                        context = this,
                        name = user.displayName.ifBlank { fallbackName },
                        email = user.email.ifBlank { fallbackEmail },
                        phone = "",
                        requestedRole = "client",
                        onSuccess = {
                            if (isFinishing || isDestroyed) return@ensureUserProfile
                            navigateAfterSignIn()
                        },
                        onError = { error ->
                            if (isFinishing || isDestroyed) return@ensureUserProfile
                            if (error.isProfileWriteForbidden()) {
                                SessionManager.setUser(this, user.uid, "client")
                                navigateAfterSignIn()
                            } else {
                                toast(getString(R.string.error_create_profile, error.message.orEmpty()))
                            }
                        }
                    )
                } else {
                    SessionManager.setUser(this, user.uid, profile.role)
                    navigateAfterSignIn()
                }
            },
            onError = { error ->
                if (isFinishing || isDestroyed) return@fetchUserProfile
                toast(getString(R.string.error_load_profile, error.message.orEmpty()))
            }
        )
    }

    private fun navigateAfterSignIn() {
        toast(getString(R.string.success_signin))
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun shouldRetryAfterReauthFailure(e: GetCredentialException): Boolean {
        if (didRetryAfterClearingState) return false
        val message = e.message.orEmpty()
        return message.contains("Account reauth failed", ignoreCase = true)
    }
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun Throwable.isProfileWriteForbidden(): Boolean {
    val text = message.orEmpty()
    return text.contains("Upsert users failed: HTTP 403", ignoreCase = true)
}
