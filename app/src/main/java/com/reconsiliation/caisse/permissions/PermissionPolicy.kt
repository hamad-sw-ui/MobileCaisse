package com.reconsiliation.caisse.permissions

/**
 * Décisions de permission testables, sans Android.
 * Les seuils d'API suivent les constantes officielles :
 * Android 12 = 31, Android 13 = 33.
 */
object PermissionPolicy {
    const val API_BLUETOOTH_CONNECT = 31
    const val API_POST_NOTIFICATIONS = 33

    const val CAMERA = "android.permission.CAMERA"
    const val RECEIVE_SMS = "android.permission.RECEIVE_SMS"
    const val READ_SMS = "android.permission.READ_SMS"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"

    fun needsBluetoothConnect(sdkInt: Int): Boolean = sdkInt >= API_BLUETOOTH_CONNECT

    fun needsNotifications(sdkInt: Int): Boolean = sdkInt >= API_POST_NOTIFICATIONS

    fun bluetoothConnectPermission(sdkInt: Int): String? =
        if (needsBluetoothConnect(sdkInt)) BLUETOOTH_CONNECT else null

    fun notificationPermission(sdkInt: Int): String? =
        if (needsNotifications(sdkInt)) POST_NOTIFICATIONS else null

    fun needsBluetoothScan(): Boolean = false

    fun canUseBluetooth(sdkInt: Int, connectGranted: Boolean): Boolean =
        !needsBluetoothConnect(sdkInt) || connectGranted

    fun decide(
        granted: Boolean,
        rationaleVisible: Boolean,
        requestAttempted: Boolean
    ): PermissionDecision {
        if (granted) return PermissionDecision.Granted
        if (!requestAttempted) return PermissionDecision.Request
        if (rationaleVisible) return PermissionDecision.Request
        return PermissionDecision.OpenSettings
    }
}

enum class PermissionDecision {
    Granted,
    Request,
    OpenSettings
}

sealed class SmsSyncResult {
    data class Success(val matchedCount: Int) : SmsSyncResult()
    object PermissionDenied : SmsSyncResult()
    data class Failed(val reason: String) : SmsSyncResult()
}

object SmsSyncOutcome {
    fun fromThrowable(error: Throwable): SmsSyncResult =
        if (error is SecurityException) SmsSyncResult.PermissionDenied
        else SmsSyncResult.Failed(error.message ?: error.javaClass.simpleName)
}
