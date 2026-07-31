package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.StaffEntity
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel

private const val ROLE_STAFF = "STAFF"
private const val ROLE_MANAGER = "MANAGER"

/**
 * Gestion des employés (B-070).
 *
 * `StaffEntity`, `StaffDao` et l'authentification par PIN existaient déjà, mais
 * aucun écran ne permettait de créer un employé : la table restait vide, et le
 * rôle `STAFF` était donc inaccessible en pratique.
 *
 * Écran réservé au rôle MANAGER (route protégée dans `MainViewModel`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val staff by viewModel.activeStaff.collectAsState()

    var editing by remember { mutableStateOf<StaffEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var toDeactivate by remember { mutableStateOf<StaffEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Gestion du personnel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Ajouter") },
                containerColor = Primary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (staff.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Aucun employé enregistré",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ajoutez vos vendeurs pour qu'ils accèdent à la caisse " +
                            "avec leur propre code PIN. Chaque vente sera associée " +
                            "à son auteur.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(staff, key = { it.id }) { member ->
                        StaffCard(
                            member = member,
                            onEdit = { editing = member; showForm = true },
                            onDeactivate = { toDeactivate = member }
                        )
                    }
                }
            }
        }

        if (showForm) {
            StaffFormDialog(
                existing = editing,
                onDismiss = { showForm = false; editing = null },
                onSave = { name, pin, role, phone ->
                    viewModel.saveStaff(name, pin, role, phone, editing) { error ->
                        showForm = false
                        editing = null
                    }
                }
            )
        }

        toDeactivate?.let { member ->
            AlertDialog(
                onDismissRequest = { toDeactivate = null },
                icon = { Icon(Icons.Default.PersonOff, null, tint = RedError) },
                title = { Text("Désactiver ${member.name} ?") },
                text = {
                    Text(
                        "Cet employé ne pourra plus se connecter. Son historique " +
                            "de ventes est conservé : rien n'est supprimé."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deactivateStaff(member)
                        toDeactivate = null
                    }) { Text("Désactiver", color = RedError) }
                },
                dismissButton = {
                    TextButton(onClick = { toDeactivate = null }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
private fun StaffCard(
    member: StaffEntity,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.Bold)
                    if (member.role == ROLE_MANAGER) {
                        Spacer(Modifier.fillMaxWidth(0.02f))
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Manager",
                            tint = Primary,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
                Text(
                    if (member.role == ROLE_MANAGER) "Manager — accès complet"
                    else "Employé — accès caisse",
                    style = MaterialTheme.typography.bodySmall
                )
                member.phone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Modifier")
            }
            IconButton(onClick = onDeactivate) {
                Icon(Icons.Default.PersonOff, "Désactiver", tint = RedError)
            }
        }
    }
}

@Composable
private fun StaffFormDialog(
    existing: StaffEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, pin: String, role: String, phone: String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var pin by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(existing?.role ?: ROLE_STAFF) }

    // À la création le PIN est obligatoire ; à la modification, le laisser vide
    // conserve le PIN existant — on ne peut pas le réafficher, il est haché.
    val pinValid = pin.length == 4 || (existing != null && pin.isEmpty())
    val canSave = name.isNotBlank() && pinValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nouvel employé" else "Modifier ${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone (facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = {
                        Text(if (existing == null) "Code PIN (4 chiffres)"
                             else "Nouveau PIN (vide = inchangé)")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Rôle", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == ROLE_STAFF,
                        onClick = { role = ROLE_STAFF },
                        label = { Text("Employé") }
                    )
                    FilterChip(
                        selected = role == ROLE_MANAGER,
                        onClick = { role = ROLE_MANAGER },
                        label = { Text("Manager") }
                    )
                }
                if (role == ROLE_MANAGER) {
                    Text(
                        "Un manager accède aux rapports, aux paramètres et peut " +
                            "supprimer des ventes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RedError
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, pin, role, phone.takeIf { it.isNotBlank() }) },
                enabled = canSave
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
