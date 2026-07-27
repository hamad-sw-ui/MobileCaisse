package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = PreferencesManager(context)

    LaunchedEffect(Unit) {
        // Reduced delay for a snappier launch, but enough to show branding
        delay(1000)
        
        try {
            // Robust check: Preference OR Database
            val db = com.reconsiliation.caisse.data.local.AppDatabase.getDatabase(context)
            val boutique: com.reconsiliation.caisse.data.local.entity.BoutiqueEntity? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    db.boutiqueDao().getBoutiqueOnce()
                } catch (e: Exception) {
                    null // If DB is empty/corrupt, we handle it
                }
            }
            
            val isSetup = prefs.isSetupComplete() || (boutique != null && boutique.isSetupComplete)
            
            if (isSetup) {
                // Ensure prefs are synced
                if (boutique?.isSetupComplete == true && !prefs.isSetupComplete()) {
                    prefs.setSetupComplete(true)
                }
                
                navController.navigate(Screen.Pin.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            } else {
                // Point de départ pour un nouvel utilisateur : Onboarding
                navController.navigate(Screen.Onboarding.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        } catch (e: Exception) {
            // Fallback for safety
            navController.navigate(Screen.Setup.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Mobile", color = Color.Yellow, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(text = "Caisse", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
    }
}
