package com.reconsiliation.caisse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Dialogue de saisie du mot de passe de sauvegarde, partagé par les trois
 * parcours (Paramètres, Clôture, Restauration) — B-142.
 *
 * Le mot de passe est saisi manuellement à chaque opération et n'est jamais
 * conservé (décision D2-C) : il ne doit donc pas être dérivé du `managerCode`,
 * qui protège déjà la base elle-même.
 *
 * L'état est mémorisé par `rememberSaveable` : une rotation d'écran en cours de
 * saisie ne fait pas perdre le contenu.
 *
 * @param isExport `true` pour un export (avertissement sur la perte du mot de
 *        passe), `false` pour une restauration.
 */
@Composable
fun BackupPasswordDialog(
    isExport: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    // Politique alignée sur BackupManager.validatePasswordStrength : la
    // vérifier ici évite un aller-retour inutile jusqu'à l'échec du chiffrement.
    val policyOk = password.length >= 8 &&
        password.any { it.isUpperCase() } &&
        password.any { it.isLowerCase() } &&
        password.any { it.isDigit() }

    val canConfirm = if (isExport) policyOk else password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = {
            Text(if (isExport) "Protéger la sauvegarde" else "Mot de passe de la sauvegarde")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = subtitle ?: if (isExport) {
                        "Choisissez un mot de passe. Il sera demandé pour restaurer " +
                            "cette sauvegarde."
                    } else {
                        "Saisissez le mot de passe utilisé lors de l'export."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (visible) {
                                    "Masquer le mot de passe"
                                } else {
                                    "Afficher le mot de passe"
                                }
                            )
                        }
                    },
                    isError = isExport && password.isNotEmpty() && !policyOk,
                    supportingText = if (isExport) {
                        {
                            Text(
                                "8 caractères minimum, avec majuscule, minuscule et chiffre",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isExport) {
                    // Sans serveur, un mot de passe oublié rend l'archive
                    // définitivement illisible : l'avertissement n'est pas
                    // contournable (B-143).
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Ce mot de passe ne peut pas être récupéré. " +
                                    "Sans lui, la sauvegarde sera définitivement illisible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = canConfirm
            ) {
                Text(if (isExport) "Exporter" else "Restaurer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
