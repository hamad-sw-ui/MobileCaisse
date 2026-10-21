package com.reconsiliation.caisse.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envoi de rapports (ex: rapport de cloture) par e-mail.
 *
 * L'envoi est delogue a une application e-mail presente sur l'appareil via
 * un Intent ACTION_SENDTO (mailto:). Si aucune application ne peut traiter
 * le message, [isConfigured] reste a false et [send] echoue avec un message
 * explicite plutot que de crasher l'application.
 */
@Singleton
open class EmailSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Indique si une application e-mail capable d'envoyer un mailto est disponible.
     */
    open val isConfigured: Boolean
        get() = intent.resolveActivity(context.packageManager) != null

    /**
     * Envoie un e-mail simple (sans piece jointe).
     *
     * @param to destinataire principal.
     * @param subject objet du message.
     * @param body corps du message.
     * @return [Result.success] si l'application e-mail a ete lancee,
     *         [Result.failure] sinon.
     */
    open fun send(to: String, subject: String, body: String): Result<Unit> {
        val target = intent
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)

        return try {
            context.startActivity(target)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val intent: Intent
        get() = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
