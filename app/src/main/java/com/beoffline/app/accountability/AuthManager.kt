package com.beoffline.app.accountability

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Sign-In (Credential Manager) → Firebase Auth.
 *
 * The web client id is looked up at RUNTIME (not R.string) because the
 * google-services.json shipped today has no OAuth client yet — the app must
 * degrade to a clear "not configured" message instead of failing the build
 * or crashing, until the Google provider is enabled in Firebase console and
 * the json re-downloaded.
 */
@Singleton
class AuthManager @Inject constructor() {

    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    val isSignedIn: Boolean get() = auth.currentUser != null

    fun isGoogleSignInConfigured(context: Context): Boolean =
        webClientId(context) != null

    /** Must be called with an Activity context (Credential Manager shows UI). */
    suspend fun signIn(activityContext: Context): Result<FirebaseUser> {
        val webClientId = webClientId(activityContext)
            ?: return Result.failure(
                IllegalStateException(
                    "Google sign-in isn't configured yet. Enable the Google provider in " +
                        "Firebase console and update google-services.json."
                )
            )

        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)

            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("Unexpected credential type."))
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = auth.signOut()

    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (resId == 0) return null
        return context.getString(resId).takeIf { it.isNotBlank() }
    }
}
