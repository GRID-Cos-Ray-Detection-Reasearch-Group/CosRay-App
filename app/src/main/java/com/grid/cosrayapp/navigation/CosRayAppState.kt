package com.grid.cosrayapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class CosRayAppState(
  val navController: NavHostController,
  val authRepository: AuthRepository,
  private val coroutineScope: CoroutineScope,
) {
  val authState = authRepository.authState
  private var guestMode = false

  val isGuestMode: Boolean
    get() = guestMode

  fun enterGuestMode() {
    guestMode = true
  }

  fun exitGuestMode() {
    guestMode = false
  }

  fun navigateTo(destination: CosRayDestination, popUpToStart: Boolean = false) {
    navController.navigate(destination.route) {
      if (popUpToStart) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
      }
      launchSingleTop = true
    }
  }

  fun onLogout() {
    coroutineScope.launch {
      authRepository.logout()
      exitGuestMode()
      navController.navigate(CosRayDestination.Login.route) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
      }
    }
  }
}

@Composable
fun rememberCosRayAppState(
  navController: NavHostController = rememberNavController(),
  authRepository: AuthRepository,
  coroutineScope: CoroutineScope = androidx.compose.runtime.rememberCoroutineScope(),
): CosRayAppState =
  remember(navController, authRepository, coroutineScope) {
    CosRayAppState(
      navController = navController,
      authRepository = authRepository,
      coroutineScope = coroutineScope,
    )
  }

@Composable
fun rememberAuthState(appState: CosRayAppState): AuthState =
  appState.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading).value
