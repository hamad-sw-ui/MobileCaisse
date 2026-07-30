package com.reconsiliation.caisse.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        navController.navigate(Screen.Setup.route) {
            popUpTo(Screen.Onboarding.route) { inclusive = true }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPage(page)
        }

        Row(
            Modifier.height(50.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { iteration ->
                val color = if (pagerState.currentPage == iteration) Primary else Color.LightGray
                Box(
                    modifier = Modifier.padding(4.dp).clip(CircleShape).background(color).size(10.dp)
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    launcher.launch(permissionsToRequest)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(if (pagerState.currentPage < 2) "Suivant" else "Autoriser & Démarrer")
        }
    }
}

@Composable
fun OnboardingPage(page: Int) {
    val title = when(page) {
        0 -> "Gérez votre Caisse"
        1 -> "Réconciliation Mobile"
        else -> "Stock en temps réel"
    }
    val description = when(page) {
        0 -> "Enregistrez vos ventes Cash, Mobile Money et Crédit en un clic."
        1 -> "L'application écoute les SMS de paiement et valide automatiquement vos ventes."
        else -> "Soyez alerté dès qu'un produit atteint son seuil critique."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, fontSize = 18.sp, textAlign = TextAlign.Center, color = Color.Gray)
    }
}
