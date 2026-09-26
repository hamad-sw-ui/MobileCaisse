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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.permissions.PermissionDecision
import com.reconsiliation.caisse.permissions.PermissionPolicy
import com.reconsiliation.caisse.ui.permissions.RefreshOnResume
import com.reconsiliation.caisse.ui.permissions.findActivity
import com.reconsiliation.caisse.ui.permissions.hasPermission
import com.reconsiliation.caisse.ui.permissions.openApplicationSettings

@Composable
fun OnboardingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val context = LocalContext.current
    val activity = context.findActivity()

    var smsGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECEIVE_SMS)) }
    var smsAsked by remember { mutableStateOf(false) }
    var notifGranted by remember {
        mutableStateOf(
            PermissionPolicy.notificationPermission(Build.VERSION.SDK_INT)
                ?.let { context.hasPermission(it) } ?: true
        )
    }
    var notifAsked by remember { mutableStateOf(false) }

    fun refreshStates() {
        smsGranted = context.hasPermission(Manifest.permission.RECEIVE_SMS)
        PermissionPolicy.notificationPermission(Build.VERSION.SDK_INT)?.let {
            notifGranted = context.hasPermission(it)
        }
    }

    RefreshOnResume { refreshStates() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsAsked = true
        smsGranted = granted
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifAsked = true
        notifGranted = granted
    }

    val smsRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS) == true
    val smsDecision = PermissionPolicy.decide(smsGranted, smsRationale, smsAsked)
    val notifPermission = PermissionPolicy.notificationPermission(Build.VERSION.SDK_INT)
    val notifRationale = notifPermission != null && activity?.shouldShowRequestPermissionRationale(notifPermission) == true
    val notifDecision = if (notifPermission == null) PermissionDecision.Granted
    else PermissionPolicy.decide(notifGranted, notifRationale, notifAsked)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page < 2) {
                OnboardingPage(page)
            } else {
                OnboardingPermissionsPage(
                    smsGranted = smsGranted,
                    smsDecision = smsDecision,
                    notificationsRequired = notifPermission != null,
                    notificationsGranted = notifGranted,
                    notificationsDecision = notifDecision,
                    onRequestSms = {
                        if (smsDecision == PermissionDecision.OpenSettings) context.openApplicationSettings()
                        else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    },
                    onRequestNotifications = {
                        val permission = notifPermission ?: return@OnboardingPermissionsPage
                        if (notifDecision == PermissionDecision.OpenSettings) context.openApplicationSettings()
                        else notifLauncher.launch(permission)
                    }
                )
            }
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
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(if (pagerState.currentPage < 2) stringResource(R.string.action_next) else stringResource(R.string.action_continue_without))
        }
    }
}

@Composable
fun OnboardingPage(page: Int) {
    val title = when (page) {
        0 -> stringResource(R.string.onboarding_title_1)
        1 -> stringResource(R.string.onboarding_title_2)
        else -> stringResource(R.string.onboarding_title_3)
    }
    val description = when (page) {
        0 -> stringResource(R.string.onboarding_desc_1)
        1 -> stringResource(R.string.onboarding_desc_2)
        else -> stringResource(R.string.onboarding_desc_3)
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

@Composable
private fun OnboardingPermissionsPage(
    smsGranted: Boolean,
    smsDecision: PermissionDecision,
    notificationsRequired: Boolean,
    notificationsGranted: Boolean,
    notificationsDecision: PermissionDecision,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.onboarding_title_3), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.permission_sms_receive_rationale), textAlign = TextAlign.Center)
        PermissionStatusRow(granted = smsGranted, decision = smsDecision, onAction = onRequestSms)
        if (notificationsRequired) {
            Text(stringResource(R.string.permission_notifications_rationale), textAlign = TextAlign.Center)
            PermissionStatusRow(granted = notificationsGranted, decision = notificationsDecision, onAction = onRequestNotifications)
        }
        Text(stringResource(R.string.permission_optional_continue), textAlign = TextAlign.Center, color = Color.Gray)
    }
}

@Composable
private fun PermissionStatusRow(granted: Boolean, decision: PermissionDecision, onAction: () -> Unit) {
    val status = if (granted) stringResource(R.string.permission_status_granted) else stringResource(R.string.permission_status_denied)
    val actionLabel = if (decision == PermissionDecision.OpenSettings) {
        stringResource(R.string.action_open_app_settings)
    } else {
        stringResource(R.string.action_authorize)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(status, fontWeight = FontWeight.Bold)
        if (!granted) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
