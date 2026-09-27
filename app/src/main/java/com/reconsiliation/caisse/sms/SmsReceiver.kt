package com.reconsiliation.caisse.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsProcessor: SmsProcessor

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun smsProcessor(): SmsProcessor
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            smsProcessor.processSms(
                context,
                messages.mapNotNull { sms ->
                    val body = sms.messageBody ?: return@mapNotNull null
                    IncomingSms(body, sms.originatingAddress, sms.timestampMillis)
                }
            ) {
                pendingResult.finish()
            }
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SmsReceiver", "Appareil redémarré : vérification des SMS manqués...")
        }
    }

    companion object {
        fun processSms(
            context: Context,
            messages: List<IncomingSms>,
            pendingResult: PendingResult? = null
        ) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            )
            entryPoint.smsProcessor().processSms(context, messages) {
                pendingResult?.finish()
            }
        }
    }
}
