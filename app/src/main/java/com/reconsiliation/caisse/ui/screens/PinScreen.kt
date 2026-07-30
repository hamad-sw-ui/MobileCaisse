package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.utils.HapticHelper

import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PinScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Code PIN requis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Entrez votre code à 4 chiffres", color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                val isFilled = pin.length > index
                Box(
                    modifier = Modifier.size(20.dp).background(
                        color = if (error) RedError else if (isFilled) Primary else Color.LightGray,
                        shape = CircleShape
                    )
                )
            }
        }

        if (error) {
            Text("Code PIN incorrect", color = RedError, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Numpad
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(80.dp))
                        } else {
                            OutlinedButton(
                                onClick = {
                                    error = false
                                    if (key == "DEL") {
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    } else if (pin.length < 4) {
                                        pin += key
                                        if (pin.length == 4) {
                                            scope.launch {
                                                val result = viewModel.checkPin(pin)
                                                if (result != null) {
                                                    viewModel.login(result.first, result.second)
                                                    navController.navigate(Screen.Home.route) {
                                                        popUpTo(Screen.Pin.route) { inclusive = true }
                                                    }
                                                } else {
                                                    HapticHelper.vibrate(context, 200)
                                                    error = true
                                                    pin = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                            ) {
                                if (key == "DEL") {
                                    Icon(Icons.Default.Backspace, contentDescription = null)
                                } else {
                                    Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
