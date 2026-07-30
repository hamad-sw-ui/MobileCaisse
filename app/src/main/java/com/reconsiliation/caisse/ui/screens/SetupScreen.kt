package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val prefs = PreferencesManager(context)

    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("MTN") }
    var momoNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    
    var showRestoreDialog by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val externalDir = context.getExternalFilesDir(null)
        if (externalDir != null) {
            val mirrorFile = java.io.File(externalDir, "caisse_mirror.db")
            if (mirrorFile.exists()) {
                showRestoreDialog = true
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configuration Boutique") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()

            // Sans ce bandeau, un échec d'écriture en base laissait l'utilisateur
            // sur l'écran de configuration sans aucune explication.
            setupError?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Informations Générales", style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la boutique") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Nom du propriétaire") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Numéro de téléphone principal") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = textFieldColors)
            
            Text("Paiements Mobiles", style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = operator == "MTN", onClick = { operator = "MTN" }, label = { Text("MTN/OM") })
                FilterChip(selected = operator == "ORANGE", onClick = { operator = "ORANGE" }, label = { Text("Orange Money") })
            }
            OutlinedTextField(value = momoNumber, onValueChange = { momoNumber = it }, label = { Text("Numéro de réception ($operator)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = textFieldColors)
            
            Text("Sécurité", style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("Code PIN (4 chiffres)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            BigButton(
                text = "Finaliser la configuration",
                onClick = {
                    if (name.isNotBlank() && pin.length == 4) {
                        scope.launch {
                            val sec = com.reconsiliation.caisse.utils.SecurityUtil
                            val salt = sec.generateSalt()
                            val hashedPin = sec.hashPinPbkdf2(pin, salt)
                            
                            val boutique = BoutiqueEntity(
                                id = 1, // Explicit ID for replacement
                                name = name.trim(),
                                ownerName = owner.trim(),
                                phoneNumber = phone.trim(),
                                operator = operator,
                                momoNumber = momoNumber.trim(),
                                isSetupComplete = true,
                                pinHash = hashedPin,
                                pinSalt = salt,
                                managerPinHash = hashedPin,
                                managerPinSalt = salt
                            )
                            
                            try {
                                // 1. Update Database
                                db.boutiqueDao().insertOrUpdate(boutique)
                                
                                // 2. Update Preferences
                                prefs.setSetupComplete(true)
                                
                                // 3. Log initial action
                                val repository = com.reconsiliation.caisse.data.repository.MainRepository(db, context)
                                repository.logAction("SETUP_COMPLETE", "Configuration initiale terminée")

                                // 4. Navigate home
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Setup.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                // Échec de la configuration initiale : l'utilisateur
                                // resterait bloqué sur un écran sans explication.
                                android.util.Log.e("SetupScreen", "Échec de la configuration", e)
                                setupError = e.message ?: "Erreur lors de la configuration"
                            }
                        }
                    }
                }
            )
        }

        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text("Anciennes données trouvées") },
                text = { Text("Une sauvegarde locale de votre boutique a été détectée. Voulez-vous restaurer vos produits, ventes et dettes ?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val externalDir = context.getExternalFilesDir(null)
                            val mirrorFile = java.io.File(externalDir, "caisse_mirror.db")
                            val repository = com.reconsiliation.caisse.data.repository.MainRepository(db, context)
                            if (repository.restoreDatabase(context, mirrorFile)) {
                                prefs.setSetupComplete(true)
                                navController.navigate(Screen.Splash.route) {
                                    popUpTo(Screen.Setup.route) { inclusive = true }
                                }
                            }
                        }
                    }) { Text("Restaurer") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) { Text("Ignorer") }
                }
            )
        }
    }
}
