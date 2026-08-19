package com.reconsiliation.caisse.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ShareManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Partage du texte simple, avec option de ciblage (ex: WhatsApp).
     */
    open fun shareText(text: String, title: String, pkg: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) {
                `package` = pkg
            }
        }

        try {
            if (pkg != null) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, title)
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            // Fallback to generic share if targeted app is missing
            if (pkg != null) {
                val genericIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Partager via"))
            }
        }
    }

    /**
     * Partage un fichier via FileProvider.
     */
    open fun shareFile(file: File, mimeType: String, title: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Envoie un SMS direct via l'application SMS par défaut (ACTION_VIEW).
     * Préserve le comportement direct sans sélecteur générique.
     */
    open fun sendSms(phone: String, message: String) {
        val uri = Uri.parse("sms:$phone?body=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to share text if SMS app fails
            shareText(message, "Envoyer par")
        }
    }
}
