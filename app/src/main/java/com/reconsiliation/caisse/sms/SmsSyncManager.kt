package com.reconsiliation.caisse.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.reconsiliation.caisse.permissions.SmsSyncOutcome
import com.reconsiliation.caisse.permissions.SmsSyncResult

object SmsSyncManager {
    /**
     * Scans the system SMS inbox for messages from known operators within the last [hours].
     * Passes found messages to SmsReceiver for processing.
     */
    fun syncMissedSms(context: Context, hours: Int = 48): SmsSyncResult {
        return try {
            val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(context)
            val lastSync = prefs.getLastSmsSyncTime()
            val messages = mutableListOf<Pair<String, String?>>()

            val fallbackSince = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
            val since = if (lastSync > 0) lastSync else fallbackSince

            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.DATE),
                "${Telephony.Sms.Inbox.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )

            cursor?.use {
                val bodyIdx = it.getColumnIndex(Telephony.Sms.Inbox.BODY)
                val addrIdx = it.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)

                while (it.moveToNext()) {
                    val body = it.getString(bodyIdx)
                    val address = it.getString(addrIdx)

                    if (isOperatorSms(body, address)) {
                        messages.add(body to address)
                    }
                }
            }

            if (messages.isNotEmpty()) {
                Log.d("SmsSyncManager", "Found ${messages.size} potential operator SMS to sync.")
                SmsReceiver.processSms(context, messages)
            }
            prefs.setLastSmsSyncTime(System.currentTimeMillis())
            SmsSyncResult.Success(messages.size)
        } catch (error: Throwable) {
            SmsSyncOutcome.fromThrowable(error)
        }
    }

    private fun isOperatorSms(body: String, address: String?): Boolean {
        val addr = address?.lowercase() ?: ""
        val b = body.lowercase()

        val isOperatorAddr = addr.contains("momo") ||
            addr.contains("mtn") ||
            addr.contains("orange") ||
            addr.contains("om") ||
            addr.length <= 6

        val containsKeywords = b.contains("recu") ||
            b.contains("reçu") ||
            b.contains("transfert") ||
            b.contains("fcfa") ||
            b.contains("ref")

        return isOperatorAddr || containsKeywords
    }
}
