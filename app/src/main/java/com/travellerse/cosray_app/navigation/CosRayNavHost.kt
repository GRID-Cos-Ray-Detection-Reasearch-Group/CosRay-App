package com.travellerse.cosray_app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.travellerse.cosray_app.core.ui.viewModelFactory
import com.travellerse.cosray_app.data.auth.AuthValidator
import com.travellerse.cosray_app.feature.auth.LoginScreen
import com.travellerse.cosray_app.feature.auth.LoginViewModel
import com.travellerse.cosray_app.feature.dashboard.DashboardScreen
import com.travellerse.cosray_app.feature.dashboard.DashboardViewModel
import com.travellerse.cosray_app.feature.device.DeviceScreen
import com.travellerse.cosray_app.feature.device.DeviceViewModel

@Composable
fun CosRayNavHost(appState: CosRayAppState) {
        NavHost(
                navController = appState.navController,
                startDestination = CosRayDestination.Login.route
        ) {
                loginDestination(appState)
                deviceDestination(appState)
                dashboardDestination(appState)
        }
}

private fun NavGraphBuilder.loginDestination(appState: CosRayAppState) {
        composable(CosRayDestination.Login.route) {
                val viewModel: LoginViewModel =
                        viewModel(
                                factory =
                                        viewModelFactory {
                                                LoginViewModel(
                                                        appState.authRepository,
                                                        AuthValidator
                                                )
                                        }
                        )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LoginScreen(
                        state = state,
                        onUsernameChange = viewModel::onUsernameChanged,
                        onEmailChange = viewModel::onEmailChanged,
                        onPasswordChange = viewModel::onPasswordChanged,
                        onDisplayNameChange = viewModel::onDisplayNameChanged,
                        onSubmit = viewModel::submit,
                        onToggleMode = viewModel::toggleCreateAccount
                )
        }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun NavGraphBuilder.deviceDestination(appState: CosRayAppState) {
        composable(CosRayDestination.Device.route) {
                val viewModel: DeviceViewModel =
                        viewModel(
                                factory =
                                        viewModelFactory {
                                                DeviceViewModel(
                                                        bleRepository =
                                                                appState.container.bleRepository,
                                                        telemetryRepository =
                                                                appState.container
                                                                        .telemetryRepository
                                                )
                                        }
                        )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val permissionsState =
                        rememberMultiplePermissionsState(
                                permissions =
                                        listOf(
                                                android.Manifest.permission.BLUETOOTH_SCAN,
                                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                        )

                LaunchedEffect(permissionsState.allPermissionsGranted) {
                        viewModel.onPermissionsChanged(permissionsState.allPermissionsGranted)
                }

                DeviceScreen(
                        state = state,
                        permissionsState = permissionsState,
                        onRequestPermissions = {
                                viewModel.onPermissionsChanged(
                                        permissionsState.allPermissionsGranted
                                )
                        },
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onNavigateToDashboard = { appState.navigateTo(CosRayDestination.Dashboard) }
                )
        }
}

private fun NavGraphBuilder.dashboardDestination(appState: CosRayAppState) {
        composable(CosRayDestination.Dashboard.route) {
                val viewModel: DashboardViewModel =
                        viewModel(
                                factory =
                                        viewModelFactory {
                                                DashboardViewModel(
                                                        telemetryRepository =
                                                                appState.container
                                                                        .telemetryRepository,
                                                        authRepository = appState.authRepository
                                                )
                                        }
                        )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                        state = state,
                        onUpload = viewModel::uploadBufferedTelemetry,
                        onMessageShown = viewModel::clearStatusMessage
                )
        }
}
