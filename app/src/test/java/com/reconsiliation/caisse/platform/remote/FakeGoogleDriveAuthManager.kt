package com.reconsiliation.caisse.platform.remote

import android.app.Activity

class FakeGoogleDriveAuthManager(
    var connected: Boolean = true,
    var accountEmail: String? = "test.boutique@gmail.com",
    var mockAccessToken: String = "fake_access_token_12345",
    var shouldFailToken: Boolean = false
) : GoogleDriveAuthManager {

    override fun isConnected(): Boolean = connected

    override fun getConnectedAccountEmail(): String? = accountEmail

    override suspend fun signIn(activity: Activity): Result<String> =
        if (connected) Result.success(accountEmail ?: "test.boutique@gmail.com")
        else Result.failure(Exception("Non connecté"))

    override suspend fun getAccessToken(): Result<String> {
        if (shouldFailToken) {
            return Result.failure(GoogleAuthException("Échec token de test"))
        }
        return Result.success(mockAccessToken)
    }

    override suspend fun signOut(): Result<Unit> {
        connected = false
        accountEmail = null
        return Result.success(Unit)
    }
}
