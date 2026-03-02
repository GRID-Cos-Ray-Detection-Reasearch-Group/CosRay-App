@file:Suppress("FunctionNaming")

package com.grid.cosrayapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.navigation.CosRayAppState
import com.grid.cosrayapp.navigation.CosRayDestination
import com.grid.cosrayapp.navigation.CosRayNavHost
import com.grid.cosrayapp.navigation.rememberAuthState
import com.grid.cosrayapp.navigation.rememberCosRayAppState
import com.grid.cosrayapp.ui.theme.CosRayAppTheme

@Composable
fun CosRayApp(authRepository: AuthRepository) {
  val appState: CosRayAppState = rememberCosRayAppState(authRepository = authRepository)
  val authState = rememberAuthState(appState)

  LaunchedEffect(authState) {
    when (authState) {
      is AuthState.Authenticated -> {
        appState.exitGuestMode()
        appState.navigateTo(CosRayDestination.Device, popUpToStart = true)
      }
      AuthState.Loading -> {
        Unit
      }
      else -> {
        Unit
      }
    }
  }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    CosRayNavHost(appState = appState)
  }
}
