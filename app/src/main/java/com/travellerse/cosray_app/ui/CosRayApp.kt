package com.travellerse.cosray_app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.travellerse.cosray_app.data.auth.AuthState
import com.travellerse.cosray_app.navigation.CosRayAppState
import com.travellerse.cosray_app.navigation.CosRayDestination
import com.travellerse.cosray_app.navigation.CosRayNavHost
import com.travellerse.cosray_app.navigation.rememberAuthState
import com.travellerse.cosray_app.navigation.rememberCosRayAppState
import com.travellerse.cosray_app.ui.theme.CosRayAppTheme

@Composable
fun CosRayApp(appState: CosRayAppState = rememberCosRayAppState()) {
    val authState = rememberAuthState(appState)

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                appState.navigateTo(CosRayDestination.Device, popUpToStart = true)
            }
            AuthState.Unauthenticated -> {
                appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
            }
            AuthState.Loading -> Unit
        }
    }

    CosRayAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CosRayNavHost(appState = appState)
        }
    }
}
