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
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val deviceId = viewModel.getDeviceId()
    var activationKey by remember { mutableStateOf("") }
    val subscription by viewModel.subscription.collectAsState()
    val boutique by viewModel.boutique.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support & Abonnement") },
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
                    Text("Statut de l'abonnement", style = MaterialTheme.typography.titleSmall)
                    subscription?.let {
                        Text(if (it.isActive) "ACTIF jusqu'au ${com.reconsiliation.caisse.utils.FormatUtil.formatDate(it.endDate)}" else "EXPIRÉ",
                            color = if (it.isActive) com.reconsiliation.caisse.ui.theme.GreenSuccess else com.reconsiliation.caisse.ui.theme.RedError,
                            fontWeight = FontWeight.Bold)
                    } ?: Text("Aucun abonnement", color = androidx.compose.ui.graphics.Color.Gray)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Identifiant de l'appareil", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(deviceId, fontWeight = FontWeight.Bold, color = Primary)
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(deviceId)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copier")
                        }
                    }
                }
            }

            Text("Renouvellement Automatique", style = MaterialTheme.typography.titleMedium, color = Primary)
            Text(
                "Choisissez un forfait et payez via Mobile Money. L'activation sera instantanée après confirmation.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubscriptionPlanCard(
                    title = "1 Mois",
                    price = "5 000 F",
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 5000) }
                )
                SubscriptionPlanCard(
                    title = "6 Mois",
                    price = "25 000 F",
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 25000) }
                )
                SubscriptionPlanCard(
                    title = "12 Mois",
                    price = "45 000 F",
                    modifier = Modifier.weight(1f),
                    onClick = { initiatePayment(context, viewModel, boutique?.operator ?: "MTN", 45000) }
                )
            }

            HorizontalDivider()

            var showManual by remember { mutableStateOf(false) }
            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "Masquer l'activation manuelle" else "Activation manuelle (Clé)")
            }

            if (showManual) {
                Text(
                    "Entrez la clé reçue par SMS si l'activation automatique n'a pas fonctionné.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = activationKey,
                    onValueChange = { activationKey = it },
                    label = { Text("Clé d'activation") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                Button(
                    onClick = { viewModel.activateSubscription(activationKey) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activationKey.isNotBlank()
                ) {
                    Text("Activer manuellement")
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
                        Text("Besoin d'aide ?", fontWeight = FontWeight.Bold)
                        Text("Contactez le support au 692971991 (WhatsApp/Appel)", style = MaterialTheme.typography.bodySmall)
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
            Text("Acheter", style = MaterialTheme.typography.labelSmall)
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
