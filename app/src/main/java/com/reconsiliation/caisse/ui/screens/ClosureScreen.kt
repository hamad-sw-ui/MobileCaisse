package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val ventes by viewModel.allVentes.collectAsState()
    
    val todayVentes = ventes.filter { DateUtils.isToday(it.date.time) }
    val cashTotal = todayVentes.filter { it.paymentMethod == "CASH" && it.status == "CONFIRMED" }.sumOf { it.amount }
    val momoTotal = todayVentes.filter { it.paymentMethod == "MOMO" && it.status == "CONFIRMED" }.sumOf { it.amount }
    val creditTotal = todayVentes.filter { it.paymentMethod == "CREDIT" }.sumOf { it.amount }
    val isLocked = todayVentes.any { it.isLocked }

    var realCash by remember { mutableStateOf("") }
    val cashDiff = (realCash.toDoubleOrNull() ?: cashTotal) - cashTotal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clôture de Journée") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (todayVentes.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Summarize, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aucune activité aujourd'hui.", style = MaterialTheme.typography.titleMedium)
                Text("Enregistrez d'abord une vente pour pouvoir clôturer votre journée.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.navigate(Screen.NewSale.route) }) {
                    Text("Faire une Vente")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Résumé du ${FormatUtil.formatDate(Date())}", style = MaterialTheme.typography.titleLarge, color = Primary)
                
                ClosureCard("Confirmé $operatorName", momoTotal)
                ClosureCard("Dettes du jour", creditTotal)
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Validation du Cash", style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Attendu système:")
                            Text(FormatUtil.formatCurrency(cashTotal), fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(
                            value = realCash,
                            onValueChange = { realCash = it },
                            label = { Text("Montant réel compté") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            prefix = { Text("FCFA ") },
                            colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                        )
                        if (realCash.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Écart:")
                                Text(
                                    FormatUtil.formatCurrency(cashDiff),
                                    color = if (cashDiff < 0) RedError else if (cashDiff > 0) com.reconsiliation.caisse.ui.theme.GreenSuccess else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                
                Text("Actions de fin de journée", style = MaterialTheme.typography.titleMedium)
                
                Button(
                    onClick = { exportToCsv(context, todayVentes) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.Description, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporter les ventes (CSV)")
                }

                Button(
                    onClick = { showBackupPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Backup, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sauvegarde Totale (.db)")
                }
                
                if (!isLocked) {
                    Button(
                        onClick = {
                            if (cashDiff < 0) {
                                viewModel.addExpense(com.reconsiliation.caisse.data.local.entity.ExpenseEntity(
                                    label = "Manquant de caisse (${FormatUtil.formatDate(Date())})",
                                    amount = kotlin.math.abs(cashDiff),
                                    date = Date(),
                                    category = "Pertes"
                                ))
                            }
                            viewModel.closeSession(realCash.toDoubleOrNull() ?: cashTotal)
                            viewModel.lockSalesForToday()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clôturer et Verrouiller")
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Lock, null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Journée clôturée et sécurisée", color = Color.Gray)
                        }
                    }
                }
            }
        }

        if (showBackupPasswordDialog) {
            com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                isExport = true,
                subtitle = "Sauvegarde complète de la base après clôture. " +
                    "Choisissez un mot de passe pour la protéger.",
                onConfirm = { password ->
                    showBackupPasswordDialog = false
                    viewModel.exportEncryptedBackup(context, password)
                },
                onDismiss = { showBackupPasswordDialog = false }
            )
        }
    }
}

@Composable
fun ClosureCard(label: String, amount: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(FormatUtil.formatCurrency(amount), fontWeight = FontWeight.Bold, color = Primary)
        }
    }
}

// La fonction locale backupDatabase() a été supprimée : elle accédait au système
// de fichiers depuis un Composable (violation MVVM) et produisait un export non
// chiffré. L'export passe désormais par MainViewModel.exportEncryptedBackup.

fun exportToCsv(context: android.content.Context, ventes: List<com.reconsiliation.caisse.data.local.entity.VenteEntity>) {
    val fileName = "ventes_${System.currentTimeMillis()}.csv"
    val fileContent = StringBuilder("ID,Date,Description,Montant,Methode,Statut\n")
    ventes.forEach {
        fileContent.append("${it.id},${it.date},${it.description},${it.amount},${it.paymentMethod},${it.status}\n")
    }
    
    try {
        val file = File(context.cacheDir, fileName)
        file.writeText(fileContent.toString())
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager le rapport"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
