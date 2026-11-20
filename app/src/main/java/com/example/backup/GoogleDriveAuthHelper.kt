package com.example.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleAccountInfo(
    val email: String,
    val displayName: String?
)

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsConsent(val intentSender: IntentSender) : DriveAuthOutcome
}

object GoogleDriveAuthHelper {

    private const val TAG = "GoogleDriveAuthHelper"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun isGoogleSignInConfigured(context: Context): Boolean {
        @Suppress("DiscouragedApi")
        val clientIdResId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (clientIdResId == 0) return false
        return context.getString(clientIdResId).isNotBlank()
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<GoogleAccountInfo> {
        return try {
            @Suppress("DiscouragedApi")
            val clientIdResId = activityContext.resources.getIdentifier(
                "default_web_client_id",
                "string",
                activityContext.packageName
            )
            val serverClientId = if (clientIdResId != 0) {
                activityContext.getString(clientIdResId)
            } else {
                ""
            }
            if (serverClientId.isBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "Direct Google Sign-In is not configured in this build. Use Save Backup (Drive / Phone) or Share to Drive instead."
                    )
                )
            }
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val response = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(
                    GoogleAccountInfo(
                        email = googleCred.id,
                        displayName = googleCred.displayName
                    )
                )
            } else {
                Result.failure(IllegalStateException("Unexpected credential type returned."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Google sign-in cancelled."))
        } catch (_: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    "No Google account found on this device. Add a Google account in device Settings first."
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google sign-in failed."))
        }
    }

    suspend fun disconnectGoogle(context: Context) {
        try {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState warning", e)
        }
    }

    suspend fun requestDriveAuthorization(context: Context): Result<DriveAuthOutcome> {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()

            val result = Identity.getAuthorizationClient(context)
                .authorize(authRequest)
                .awaitResult()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return Result.failure(IllegalStateException("Consent dialog unavailable."))
                Result.success(DriveAuthOutcome.NeedsConsent(pendingIntent.intentSender))
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Google Drive access token was empty."))
                } else {
                    Result.success(DriveAuthOutcome.Authorized(token))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Drive authorization failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to authorize Google Drive."))
        }
    }

    suspend fun authorizeDriveSilent(context: Context): String? {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .authorize(authRequest)
                .awaitResult()
            if (result.hasResolution()) null else result.accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent Drive authorization failed", e)
            null
        }
    }

    fun extractAccessTokenFromIntent(context: Context, intentData: Intent?): Result<String> {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(intentData)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("Google Drive permission was not granted."))
            } else {
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google Drive consent failed."))
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
