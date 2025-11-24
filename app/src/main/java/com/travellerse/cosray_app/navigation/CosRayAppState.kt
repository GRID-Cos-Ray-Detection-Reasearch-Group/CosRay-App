package com.travellerse.cosray_app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.travellerse.cosray_app.core.di.AppContainer
import com.travellerse.cosray_app.core.di.LocalAppContainer
import com.travellerse.cosray_app.data.auth.AuthRepository
import com.travellerse.cosray_app.data.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class CosRayAppState(
        val navController: NavHostController,
        private val appContainer: AppContainer,
        private val coroutineScope: CoroutineScope
) {
    val container: AppContainer
        get() = appContainer

    val authRepository: AuthRepository = appContainer.authRepository
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
        appContainer: AppContainer = LocalAppContainer.current,
        coroutineScope: CoroutineScope = androidx.compose.runtime.rememberCoroutineScope()
): CosRayAppState =
        remember(navController, appContainer, coroutineScope) {
            CosRayAppState(navController, appContainer, coroutineScope)
        }

@Composable
fun rememberAuthState(appState: CosRayAppState): AuthState =
        appState.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading).value
