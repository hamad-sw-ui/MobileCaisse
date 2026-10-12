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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.utils.HapticHelper

import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

/**
 * Handles PIN digit entry and backspace logic, capping length at [maxDigits].
 * Used by [PinScreen] in production and verified by tests.
 */
fun handlePinKeyPress(
    currentPin: String,
    key: String,
    maxDigits: Int = 4
): String {
    return if (key == "DEL") {
        if (currentPin.isNotEmpty()) currentPin.dropLast(1) else ""
    } else if (currentPin.length < maxDigits) {
        currentPin + key
    } else {
        currentPin
    }
}

@Composable
fun PinScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = hiltViewModel()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    PinContent(
        pin = pin,
        error = error,
        onKeyPress = { key ->
            error = false
            val newPin = handlePinKeyPress(pin, key)
            if (newPin != pin) {
                pin = newPin
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
        }
    )
}

@Composable
fun PinContent(
    pin: String,
    error: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.pin_required), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.pin_enter_desc), color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                val isFilled = pin.length > index
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("pin_dot_$index")
                        .background(
                            color = if (error) RedError else if (isFilled) Primary else Color.LightGray,
                            shape = CircleShape
                        )
                )
            }
        }

        if (error) {
            Text(stringResource(R.string.pin_invalid), color = RedError, modifier = Modifier.padding(top = 8.dp))
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
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        Icons.Default.Backspace,
                                        contentDescription = stringResource(R.string.action_delete)
                                    )
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
