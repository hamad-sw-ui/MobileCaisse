package com.reconsiliation.caisse.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.reconsiliation.caisse.ui.theme.CAISSETheme

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    CAISSETheme {
        SplashScreen(rememberNavController())
    }
}
