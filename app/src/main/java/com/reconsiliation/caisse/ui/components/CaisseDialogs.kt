package com.reconsiliation.caisse.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun AddCategoryDialog(
    type: String, // "PRODUCT" or "EXPENSE"
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle Catégorie (${if (type == "PRODUCT") "Produit" else "Dépense"})") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom de la catégorie") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
