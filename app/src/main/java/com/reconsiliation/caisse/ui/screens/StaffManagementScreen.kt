package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.StaffEntity
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.StaffViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import kotlinx.coroutines.launch

private const val ROLE_STAFF = "STAFF"
private const val ROLE_MANAGER = "MANAGER"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(navController: NavController) {
    val viewModel: StaffViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var editing by remember { mutableStateOf<StaffEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var toDeactivate by remember { mutableStateOf<StaffEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val staffSaveError = stringResource(R.string.error_staff_save)
    val staffDeactivateError = stringResource(R.string.error_staff_deactivate)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_staff_mgmt)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.action_add)) },
                containerColor = Primary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.activeStaff.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.no_staff_registered),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.no_staff_registered_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.activeStaff, key = { it.id }) { member ->
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
                onSave = { name, employeeCode, pin, role, phone ->
                    viewModel.saveStaff(name, pin, role, phone, employeeCode, editing) { error ->
                        if (error == null) {
                            showForm = false
                            editing = null
                        } else {
                            scope.launch { snackbar.showSnackbar(staffSaveError) }
                        }
                    }
                }
            )
        }

        toDeactivate?.let { member ->
            AlertDialog(
                onDismissRequest = { toDeactivate = null },
                icon = { Icon(Icons.Default.PersonOff, null, tint = RedError) },
                title = { Text(stringResource(R.string.dialog_deactivate_staff_title, member.name)) },
                text = {
                    Text(stringResource(R.string.deactivate_staff_warning_desc))
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deactivateStaff(member) { error ->
                            toDeactivate = null
                            if (error != null) {
                                scope.launch { snackbar.showSnackbar(staffDeactivateError) }
                            }
                        }
                    }) { Text(stringResource(R.string.action_deactivate), color = RedError) }
                },
                dismissButton = {
                    TextButton(onClick = { toDeactivate = null }) { Text(stringResource(R.string.action_cancel)) }
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
                        Spacer(Modifier.padding(4.dp))
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = stringResource(R.string.label_manager),
                            tint = Primary,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
                Text(
                    if (member.role == ROLE_MANAGER) stringResource(R.string.label_role_manager_desc)
                    else stringResource(R.string.label_role_staff_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                member.phone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDeactivate) {
                Icon(Icons.Default.PersonOff, stringResource(R.string.action_deactivate), tint = RedError)
            }
        }
    }
}

@Composable
private fun StaffFormDialog(
    existing: StaffEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, employeeCode: String?, pin: String, role: String, phone: String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var employeeCode by rememberSaveable { mutableStateOf(existing?.employeeCode ?: "") }
    var pin by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(existing?.role ?: ROLE_STAFF) }

    val pinValid = pin.length == 4 || (existing != null && pin.isEmpty())
    val canSave = name.isNotBlank() && pinValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.title_new_staff) else stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.label_phone_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = employeeCode,
                    onValueChange = { employeeCode = it },
                    label = { Text(stringResource(R.string.label_employee_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = {
                        Text(if (existing == null) stringResource(R.string.label_pin_4_digits)
                             else stringResource(R.string.label_new_pin_hint))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.label_role), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == ROLE_STAFF,
                        onClick = { role = ROLE_STAFF },
                        label = { Text(stringResource(R.string.label_staff)) }
                    )
                    FilterChip(
                        selected = role == ROLE_MANAGER,
                        onClick = { role = ROLE_MANAGER },
                        label = { Text(stringResource(R.string.label_manager)) }
                    )
                }
                if (role == ROLE_MANAGER) {
                    Text(
                        stringResource(R.string.label_manager_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = RedError
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, employeeCode.takeIf { it.isNotBlank() }, pin, role, phone.takeIf { it.isNotBlank() }) },
                enabled = canSave
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
