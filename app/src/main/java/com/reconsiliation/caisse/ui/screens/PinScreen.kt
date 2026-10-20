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

/**
 * Computes the anti-brute-force lockout delay (in ms) after a failed PIN attempt.
 * Pure and unit-testable: exponential backoff starting at 1s after 3 failures,
 * doubling up to a 30s ceiling. 0 means no lockout.
 */
fun pinLockoutDelayMs(failedAttempts: Int): Long {
    if (failedAttempts < 3) return 0L
    val exponent = failedAttempts - 3
    val cappedExponent = exponent.coerceAtMost(5)
    return (1000L shl cappedExponent).coerceAtMost(30_000L)
}

@Composable
fun PinScreen(navController:NavController) {
    val context = LocalContext.current
    val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(context)
    val viewModel: MainViewModel = hiltViewModel()
    var pin by remember { mutableStateOf("") }
    var employeeCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(prefs.getFailedAttempts()) }
    var lockoutUntil by remember { mutableStateOf(prefs.getLockTime()) }
    val lockoutRemaining = lockoutUntil - System.currentTimeMillis()

    // Ticker: re-compose every second while the lockout is active so the countdown text updates.
    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            kotlinx.coroutines.delay(250)
        }
    }

    val scope = rememberCoroutineScope()

    PinContent(
        pin = pin,
        employeeCode = employeeCode,
        error = error,
        lockoutSeconds = if (lockoutRemaining > 0) ((lockoutRemaining + 999) / 1000).toInt() else 0,
        onKeyPress = { key ->
            if (lockoutUntil > System.currentTimeMillis()) return@PinContent
            error = false
            val newPin = handlePinKeyPress(pin, key)
            if (newPin != pin) {
                pin = newPin
                if (pin.length == 4) {
                    scope.launch {
                        val result = if (employeeCode.isNotBlank()) {
                            viewModel.checkStaffLogin(employeeCode, pin)
                        } else {
                            viewModel.checkPin(pin)
                        }
                        if (result != null) {
                            viewModel.login(result.first, result.second)
                            prefs.resetFailedAttempts()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Pin.route) { inclusive = true }
                            }
                        } else {
                            HapticHelper.vibrate(context, 200)
                            error = true
                            pin = ""
                            prefs.incrementFailedAttempts()
                            failedAttempts++
                            val delayMs = pinLockoutDelayMs(failedAttempts)
                            if (delayMs > 0) lockoutUntil = System.currentTimeMillis() + delayMs
                            if (delayMs > 0) prefs.lock((delayMs / 60000).coerceAtLeast(1).toInt())
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
    employeeCode: String = "",
    error: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    lockoutSeconds: Int = 0
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.pin_required), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.pin_enter_desc), color = Color.Gray)

        OutlinedTextField(
            value = employeeCode,
            onValueChange = { },
            label = { Text(stringResource(R.string.label_employee_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        if (lockoutSeconds > 0) {
            Text(
                stringResource(R.string.pin_locked_countdown, lockoutSeconds),
                color = RedError,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (error) {
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

