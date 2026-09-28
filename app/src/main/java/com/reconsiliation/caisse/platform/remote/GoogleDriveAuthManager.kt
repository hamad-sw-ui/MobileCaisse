package com.reconsiliation.caisse.platform.remote

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    fun getSignInClient(): GoogleSignInClient?
    suspend fun getAccessToken(): Result<String>
    suspend fun signOut(): Result<Unit>
}

@Singleton
class PlayServicesGoogleDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : GoogleDriveAuthManager {

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(Scopes.DRIVE_APPFOLDER))
            .build()
    }

    override fun isConnected(): Boolean {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            account != null && GoogleSignIn.hasPermissions(account, Scope(Scopes.DRIVE_APPFOLDER))
        } catch (e: Exception) {
            false
        }
    }

    override fun getConnectedAccountEmail(): String? {
        return try {
            GoogleSignIn.getLastSignedInAccount(context)?.email
        } catch (e: Exception) {
            null
        }
    }

    override fun getSignInClient(): GoogleSignInClient? {
        return try {
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw GoogleAuthException("Aucun compte Google connecté")

            val gmsAccount = account.account
                ?: throw GoogleAuthException("Compte Google introuvable sur l'appareil")

            val scopeString = "oauth2:$DRIVE_APPDATA_SCOPE"
            try {
                GoogleAuthUtil.getToken(context, gmsAccount, scopeString)
            } catch (e: Exception) {
                throw GoogleAuthException("Échec de récupération du jeton OAuth2: ${e.message}")
            }
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            getSignInClient()?.signOut()
            Unit
        }
    }
}

class GoogleAuthException(message: String) : Exception(message)
