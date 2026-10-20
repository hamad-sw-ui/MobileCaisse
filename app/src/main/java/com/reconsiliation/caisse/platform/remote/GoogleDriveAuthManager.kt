package com.reconsiliation.caisse.platform.remote

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class GoogleAuthState {
    CONNECTED,
    DISCONNECTED,
    AUTH_REQUIRED,
    GMS_UNAVAILABLE
}

interface GoogleDriveAuthManager {
    fun isConnected(): Boolean
    fun getConnectedAccountEmail(): String?
    suspend fun signIn(activity: Activity): Result<String>
    suspend fun getAccessToken(): Result<String>
    suspend fun signOut(): Result<Unit>
}

@Singleton
class PlayServicesGoogleDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : GoogleDriveAuthManager {

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val NOT_CONFIGURED = "REPLACE_WITH_OAUTH_WEB_CLIENT_ID"
    }

    private val prefs: PreferencesManager by lazy { PreferencesManager(context) }

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    private val serverClientId: String? by lazy {
        runCatching { context.getString(com.reconsiliation.caisse.R.string.default_web_client_id) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != NOT_CONFIGURED }
    }

    override fun isConnected(): Boolean = !prefs.getGoogleAccountEmail().isNullOrBlank()

    override fun getConnectedAccountEmail(): String? = prefs.getGoogleAccountEmail()

    override suspend fun signIn(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        val clientId = serverClientId
            ?: return@withContext Result.failure(
                GoogleAuthException(context.getString(com.reconsiliation.caisse.R.string.google_auth_not_configured))
            )
        runCatching {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential as? CustomCredential
                ?: throw GoogleAuthException("Identifiant Google invalide")
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (idToken.isNullOrBlank()) {
                throw GoogleAuthException("Jeton d'identification Google absent")
            }
            val email = extractEmailFromIdToken(idToken)
                ?: throw GoogleAuthException("E-mail Google introuvable")
            prefs.saveGoogleAccount(email, email)
            email
        }.recoverCatching { error ->
            if (error is GetCredentialException) {
                throw GoogleAuthException("Connexion Google annulée ou impossible : ${error.type}")
            }
            throw error
        }
    }

    override suspend fun getAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val email = prefs.getGoogleAccountEmail()
                ?: throw GoogleAuthException("Aucun compte Google connecté")
            val accountName = prefs.getGoogleAccountName() ?: email
            val scopeString = "oauth2:$DRIVE_APPDATA_SCOPE"
            try {
                GoogleAuthUtil.getToken(context, accountName, scopeString)
            } catch (e: Exception) {
                throw GoogleAuthException("Échec de récupération du jeton OAuth2: ${e.message}")
            }
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.clearGoogleAccount()
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            Unit
        }
    }

    private fun extractEmailFromIdToken(idToken: String?): String? {
        val payloadSegment = idToken?.split(".")?.getOrNull(1) ?: return null
        return runCatching {
            val padded = payloadSegment.padEnd((payloadSegment.length + 3) / 4 * 4, '=')
            val decoded = String(Base64.decode(padded, Base64.URL_SAFE), Charsets.UTF_8)
            JSONObject(decoded).optString("email").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

class GoogleAuthException(message: String) : Exception(message)
