package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(navController: NavController, customerId: Long) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val allCustomers by viewModel.allCustomers.collectAsState(initial = emptyList())
    val customer: CustomerEntity? = allCustomers.find { it.id == customerId }
    val currency by viewModel.currency.collectAsState()
    val customerVentes by viewModel.getVentesByCustomer(customerId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: "Profil Client") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    customer?.let {
                        IconButton(onClick = { viewModel.generateCustomerStatement(context, it) }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Relevé PDF")
                        }
                    }
                }
            )
        }
    ) { padding ->
        customer?.let { data ->
            Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                // Info Summary Card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Statut VIP:")
                            Switch(
                                checked = data.isVip,
                                onCheckedChange = { viewModel.updateVipStatus(data.id, it) }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Téléphone:")
                            Text(data.phone, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Dette Actuelle:")
                                Text(FormatUtil.formatCurrency(data.totalDebt, currency), color = RedError, fontWeight = FontWeight.Bold)
                            }
                            if (data.totalDebt > 0) {
                                Button(
                                    onClick = { viewModel.sendDebtReminder(context, data) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Send, null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rappel")
                                }
                            }
                        }
                        if (data.creditBalance > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Avoir (Crédit Client):")
                                Text(FormatUtil.formatCurrency(data.creditBalance, currency), color = GreenSuccess, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Points Fidélité:")
                            Text("${data.loyaltyPoints}", color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Dépensé:")
                            Text(FormatUtil.formatCurrency(data.totalSpent, currency), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Historique des Transactions", style = MaterialTheme.typography.titleMedium, color = Primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (customerVentes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Aucune transaction enregistrée", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(customerVentes) { data ->
                            ListItem(
                                headlineContent = { Text(FormatUtil.formatDate(data.vente.date)) },
                                supportingContent = { Text(data.vente.description.take(50)) },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(FormatUtil.formatCurrency(data.vente.amount, currency), fontWeight = FontWeight.Bold)
                                        Text(data.vente.paymentMethod, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                modifier = Modifier.clickable { navController.navigate("sale_detail/${data.vente.id}") }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
