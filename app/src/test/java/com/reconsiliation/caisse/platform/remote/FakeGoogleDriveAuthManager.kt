package com.reconsiliation.caisse.platform.remote

import com.google.android.gms.auth.api.signin.GoogleSignInClient

class FakeGoogleDriveAuthManager(
    var connected: Boolean = true,
    var accountEmail: String? = "test.boutique@gmail.com",
    var mockAccessToken: String = "fake_access_token_12345",
    var shouldFailToken: Boolean = false
) : GoogleDriveAuthManager {

    override fun isConnected(): Boolean = connected

    override fun getConnectedAccountEmail(): String? = accountEmail

    override fun getSignInClient(): GoogleSignInClient? = null

    override suspend fun getAccessToken(): Result<String> {
        if (shouldFailToken) {
            return Result.failure(GoogleAuthException("Échec token de test"))
        }
        if (!connected) {
            return Result.failure(GoogleAuthException("Aucun compte Google connecté"))
        }
        return Result.success(mockAccessToken)
    }

    override suspend fun signOut(): Result<Unit> {
        connected = false
        accountEmail = null
        return Result.success(Unit)
    }
}
