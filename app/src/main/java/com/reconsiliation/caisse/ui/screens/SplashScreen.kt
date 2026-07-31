package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    val viewModel: com.reconsiliation.caisse.ui.viewmodel.MainViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()

    LaunchedEffect(Unit) {
        // Reduced delay for a snappier launch, but enough to show branding
        delay(1000)
        
        try {
            // La vérification (préférence + base) est portée par le ViewModel :
            // un Composable n'ouvre pas la base directement (CODING_RULES §1).
            val isSetup = viewModel.isSetupComplete()

            if (isSetup) {
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
