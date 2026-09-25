package com.reconsiliation.caisse.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@Composable
fun AddCategoryDialog(
    type: String, // "PRODUCT" or "EXPENSE"
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val typeLabel = if (type == "PRODUCT") stringResource(R.string.type_product) else stringResource(R.string.type_expense)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_category_title, typeLabel)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_category_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
