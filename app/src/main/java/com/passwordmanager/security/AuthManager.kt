package com.passwordmanager.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AuthManager(private val activity: FragmentActivity) {

    private val biometricManager = BiometricManager.from(activity)

    fun canAuthenticate(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS ||
                biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)

        val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d("AuthManager", "Authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d("AuthManager", "Authentication error: $errorCode - $errString")
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d("AuthManager", "Authentication failed - fingerprint not recognized")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)

        val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: Can combine BIOMETRIC_STRONG and DEVICE_CREDENTIAL
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authenticate")
                .setSubtitle("Verify your identity to continue")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        } else {
            // API 29 and below
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    // Use biometric
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Authenticate")
                        .setSubtitle("Use your fingerprint to continue")
                        .setNegativeButtonText("Cancel")
                        .build()
                }
                else -> {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Authenticate")
                        .setSubtitle("Enter your PIN, pattern, or password")
                        .setDeviceCredentialAllowed(true)
                        .build()
                }
            }
        }

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("AuthManager", "Error starting authentication", e)
            onError("Failed to start authentication: ${e.message}")
        }
    }
}