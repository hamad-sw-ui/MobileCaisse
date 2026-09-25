package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsErrorScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MainViewModel = hiltViewModel()
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val errors by viewModel.smsErrors.collectAsState()
    val allVentes by viewModel.allVentes.collectAsState()
    
    val pendingVentes = allVentes.filter { (it.paymentMethod == "MOMO" || it.paymentMethod == "MIXED") && it.status == "PENDING" }

    var selectedError by remember { mutableStateOf<com.reconsiliation.caisse.data.local.entity.SmsErrorEntity?>(null) }
    var showResolveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sms_log, operatorName)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (errors.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.SmsFailed, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.error_unrecognized_sms), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(stringResource(R.string.error_sms_not_processed, operatorName), style = MaterialTheme.typography.bodyMedium, color = OrangeWarning)
                    
                    var clipboardText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = clipboardText,
                        onValueChange = { clipboardText = it },
                        label = { Text(stringResource(R.string.hint_paste_momo_text)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        trailingIcon = {
                            if (clipboardText.isNotBlank()) {
                                IconButton(onClick = {
                                    com.reconsiliation.caisse.sms.SmsReceiver.processSms(context, listOf(clipboardText to "MANUAL_CLIPBOARD"))
                                    clipboardText = ""
                                }) {
                                    Icon(Icons.Default.Send, stringResource(R.string.action_analyze))
                                }
                            }
                        },
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(errors) { error ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(error.sender ?: stringResource(R.string.label_unknown), style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                                Text(FormatUtil.formatDate(error.date), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error.body, style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        selectedError = error
                                        showResolveDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_link_sale))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showResolveDialog && selectedError != null) {
            AlertDialog(
                onDismissRequest = { showResolveDialog = false },
                title = { Text(stringResource(R.string.dialog_select_sale)) },
                text = {
                    if (pendingVentes.isEmpty()) {
                        Text(stringResource(R.string.error_no_pending_momo_sales))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.dialog_choose_sale_desc))
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(pendingVentes) { vente ->
                                    ListItem(
                                        headlineContent = { Text(vente.description) },
                                        supportingContent = { Text(stringResource(R.string.label_amount_date_sep, FormatUtil.formatCurrency(vente.amount), FormatUtil.formatDate(vente.date))) },
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedError?.let { err ->
                                                viewModel.resolveSmsError(err, vente.id)
                                            }
                                            showResolveDialog = false
                                        },
                                        leadingContent = { Icon(Icons.Default.ListAlt, null) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showResolveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }
}
