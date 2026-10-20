package com.reconsiliation.caisse.data.prefs

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("caisse_prefs", Context.MODE_PRIVATE)

    fun isSetupComplete(): Boolean = prefs.getBoolean("setup_complete", false)
    fun setSetupComplete(complete: Boolean) = prefs.edit().putBoolean("setup_complete", complete).apply()

    fun getLastSmsSyncTime(): Long = prefs.getLong("last_sms_sync", 0L)
    fun setLastSmsSyncTime(time: Long) = prefs.edit().putLong("last_sms_sync", time).apply()

    fun getFailedAttempts(): Int = prefs.getInt("failed_attempts", 0)
    fun incrementFailedAttempts() = prefs.edit().putInt("failed_attempts", getFailedAttempts() + 1).apply()
    fun resetFailedAttempts() = prefs.edit().putInt("failed_attempts", 0).apply()

    fun isLocked(): Boolean {
        val lockTime = prefs.getLong("lock_time", 0)
        if (lockTime == 0L) return false
        val now = System.currentTimeMillis()
        if (now > lockTime) {
            prefs.edit().remove("lock_time").apply()
            return false
        }
        return true
    }

    fun lock(minutes: Int) {
        val lockUntil = System.currentTimeMillis() + (minutes * 60 * 1000)
        prefs.edit().putLong("lock_time", lockUntil).apply()
    }

    fun getLockTime(): Long = prefs.getLong("lock_time", 0L)

    // ==================== Sauvegarde Distante ====================

    fun isRemoteBackupEnabled(): Boolean = prefs.getBoolean("remote_backup_enabled", false)
    fun setRemoteBackupEnabled(enabled: Boolean) = prefs.edit().putBoolean("remote_backup_enabled", enabled).apply()

    fun isWifiOnlyBackupEnabled(): Boolean = prefs.getBoolean("wifi_only_backup_enabled", true)
    fun setWifiOnlyBackupEnabled(wifiOnly: Boolean) = prefs.edit().putBoolean("wifi_only_backup_enabled", wifiOnly).apply()

    fun getLastRemoteBackupTime(): Long = prefs.getLong("last_remote_backup_time", 0L)
    fun setLastRemoteBackupTime(time: Long) = prefs.edit().putLong("last_remote_backup_time", time).apply()

    fun getLastRemoteBackupStatus(): String = prefs.getString("last_remote_backup_status", "") ?: ""
    fun setLastRemoteBackupStatus(status: String) = prefs.edit().putString("last_remote_backup_status", status).apply()
}
