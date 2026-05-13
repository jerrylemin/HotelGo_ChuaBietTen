package com.example.hotelapp_test2.ui.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import java.util.concurrent.Executors

object GoogleCredentialStateManager {
    fun clear(context: Context, onComplete: (() -> Unit)? = null) {
        val credentialManager = CredentialManager.create(context)
        val executor = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())

        fun finish() {
            executor.shutdown()
            if (onComplete != null) {
                mainHandler.post { onComplete() }
            }
        }

        credentialManager.clearCredentialStateAsync(
            request = ClearCredentialStateRequest(),
            cancellationSignal = null,
            executor = executor,
            callback = object : CredentialManagerCallback<Void?, ClearCredentialException> {
                override fun onResult(result: Void?) {
                    finish()
                }

                override fun onError(e: ClearCredentialException) {
                    finish()
                }
            }
        )
    }
}
