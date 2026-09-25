package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(navController: NavController) {
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val deviceId = viewModel.getDeviceId()
    var activationKey by remember { mutableStateOf("") }
    val subscription by viewModel.subscription.collectAsState()
    val boutique by viewModel.boutique.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_support_subscription)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.label_subscription_status), style = MaterialTheme.typography.titleSmall)
                    subscription?.let {
                        Text(if (it.isActive) stringResource(R.string.status_active_until, FormatUtil.formatDate(it.endDate)) else stringResource(R.string.status_expired),
                            color = if (it.isActive) com.reconsiliation.caisse.ui.theme.GreenSuccess else com.reconsiliation.caisse.ui.theme.RedError,
                            fontWeight = FontWeight.Bold)
                    } ?: Text(stringResource(R.string.no_subscription), color = androidx.compose.ui.graphics.Color.Gray)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.label_device_id), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(deviceId, fontWeight = FontWeight.Bold, color = Primary)
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(deviceId)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                        }
                    }
                }
            }

            Text(stringResource(R.string.label_auto_renewal), style = MaterialTheme.typography.titleMedium, color = Primary)
            Text(
                stringResource(R.string.subscription_instruction),
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubscriptionPlanCard(
                    title = stringResource(R.string.plan_1_month),
                    price = stringResource(R.string.label_price_f, "5 000"),
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 5000) }
                )
                SubscriptionPlanCard(
                    title = stringResource(R.string.plan_6_months),
                    price = stringResource(R.string.label_price_f, "25 000"),
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 25000) }
                )
                SubscriptionPlanCard(
                    title = stringResource(R.string.plan_12_months),
                    price = stringResource(R.string.label_price_f, "45 000"),
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 45000) }
                )
            }

            HorizontalDivider()

            var showManual by remember { mutableStateOf(false) }
            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) stringResource(R.string.action_hide_manual_activation) else stringResource(R.string.action_manual_activation))
            }

            if (showManual) {
                Text(
                    stringResource(R.string.manual_activation_instruction),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = activationKey,
                    onValueChange = { activationKey = it },
                    label = { Text(stringResource(R.string.label_activation_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                Button(
                    onClick = { viewModel.activateSubscription(activationKey) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activationKey.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_activate_manually))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Help, null, tint = Primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.label_need_help), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.support_contact_info, "692971991"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionPlanCard(title: String, price: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(price, color = Primary, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.action_buy), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun initiatePayment(context: android.content.Context, viewModel: MainViewModel, operator: String, amount: Int) {
    val ussdCode = viewModel.getPaymentUssd(operator, amount)
    if (ussdCode.isNotBlank()) {
        val intent = android.content.Intent(android.content.Intent.ACTION_CALL).apply {
            data = android.net.Uri.parse(ussdCode)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to dialer if CALL permission not granted
            val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse(ussdCode)
            }
            context.startActivity(dialIntent)
        }
    }
}
