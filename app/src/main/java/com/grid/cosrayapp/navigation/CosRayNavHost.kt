@file:Suppress("FunctionNaming", "LongMethod")

package com.grid.cosrayapp.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.grid.cosrayapp.BuildConfig
import com.grid.cosrayapp.R
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.feature.auth.LoginScreen
import com.grid.cosrayapp.feature.auth.LoginViewModel
import com.grid.cosrayapp.feature.auth.RegisterScreen
import com.grid.cosrayapp.feature.auth.RegisterViewModel
import com.grid.cosrayapp.feature.dashboard.DashboardScreen
import com.grid.cosrayapp.feature.dashboard.DashboardViewModel
import com.grid.cosrayapp.feature.device.DeviceScreen
import com.grid.cosrayapp.feature.device.DeviceViewModel
import com.grid.cosrayapp.feature.settings.SettingsScreen
import com.grid.cosrayapp.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun CosRayNavHost(appState: CosRayAppState) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val authState by appState.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading)
  val isAuthenticated = authState is AuthState.Authenticated

  // Get current destination for highlighting
  val currentDestination =
          appState.navController.currentBackStackEntryAsState().value?.destination?.route

  ModalNavigationDrawer(
          drawerState = drawerState,
          drawerContent = {
            val drawerWidthFraction = 0.75f
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(drawerWidthFraction)) {
              Spacer(Modifier.height(12.dp))
              Text(
                      text = stringResource(R.string.app_name),
                      modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                      style = MaterialTheme.typography.headlineMedium,
              )
              NavigationDrawerItem(
                      label = {
                        Text(
                                stringResource(R.string.device_scan_title),
                                style = MaterialTheme.typography.titleMedium,
                        )
                      },
                      selected = currentDestination == CosRayDestination.Device.route,
                      onClick = {
                        scope.launch { drawerState.close() }
                        appState.navigateTo(CosRayDestination.Device)
                      },
                      modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
              )
              NavigationDrawerItem(
                      label = {
                        Text(
                                stringResource(R.string.device_dashboard_action),
                                style = MaterialTheme.typography.titleMedium,
                        )
                      },
                      selected = currentDestination == CosRayDestination.Dashboard.route,
                      onClick = {
                        scope.launch { drawerState.close() }
                        appState.navigateTo(CosRayDestination.Dashboard)
                      },
                      modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
              )

              NavigationDrawerItem(
                      label = {
                        Text(
                                stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleMedium,
                        )
                      },
                      selected = currentDestination == CosRayDestination.Settings.route,
                      onClick = {
                        scope.launch { drawerState.close() }
                        appState.navigateTo(CosRayDestination.Settings)
                      },
                      modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
              )

              // API Test menu (Debug only)
              if (BuildConfig.DEBUG) {
                NavigationDrawerItem(
                        label = {
                          Text(
                                  stringResource(R.string.api_test_title),
                                  style = MaterialTheme.typography.titleMedium,
                          )
                        },
                        selected = currentDestination == CosRayDestination.ApiTest.route,
                        onClick = {
                          scope.launch { drawerState.close() }
                          appState.navigateTo(CosRayDestination.ApiTest)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
              }

              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

              if (isAuthenticated) {
                NavigationDrawerItem(
                        label = {
                          Text(
                                  stringResource(R.string.device_logout_action),
                                  style = MaterialTheme.typography.titleMedium,
                          )
                        },
                        selected = false,
                        onClick = {
                          scope.launch { drawerState.close() }
                          appState.onLogout()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
              } else {
                NavigationDrawerItem(
                        label = {
                          Text(
                                  stringResource(R.string.device_login_action),
                                  style = MaterialTheme.typography.titleMedium,
                          )
                        },
                        selected = false,
                        onClick = {
                          scope.launch { drawerState.close() }
                          appState.exitGuestMode()
                          appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
              }
            }
          },
  ) {
    NavHost(
            navController = appState.navController,
            startDestination = CosRayDestination.Device.route,
    ) {
      loginDestination(appState)
      registerDestination(appState)
      deviceDestination(appState, onOpenDrawer = { scope.launch { drawerState.open() } })
      dashboardDestination(onOpenDrawer = { scope.launch { drawerState.open() } })
      settingsDestination(appState, onOpenDrawer = { scope.launch { drawerState.open() } })
      // API Test destination (Debug only)
      if (BuildConfig.DEBUG) {
        apiTestDestination(onOpenDrawer = { scope.launch { drawerState.open() } })
      }
    }
  }
}

private fun NavGraphBuilder.loginDestination(appState: CosRayAppState) {
  composable(CosRayDestination.Login.route) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
            state = state,
            onUsernameChange = viewModel::onUsernameChanged,
            onPasswordChange = viewModel::onPasswordChanged,
            onSubmit = viewModel::submit,
            onNavigateToRegister = { appState.navigateTo(CosRayDestination.Register) },
            onContinueAsGuest = {
              appState.enterGuestMode()
              appState.navigateTo(CosRayDestination.Device, popUpToStart = true)
            },
    )
  }
}

private fun NavGraphBuilder.registerDestination(appState: CosRayAppState) {
  composable(CosRayDestination.Register.route) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterScreen(
            state = state,
            onUsernameChange = viewModel::onUsernameChanged,
            onEmailChange = viewModel::onEmailChanged,
            onPasswordChange = viewModel::onPasswordChanged,
            onSubmit = viewModel::submit,
            onNavigateToLogin = {
              appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
            },
    )
  }
}

// MVVM Model - view model -view
@OptIn(ExperimentalPermissionsApi::class)
private fun NavGraphBuilder.deviceDestination(appState: CosRayAppState, onOpenDrawer: () -> Unit) {
  composable(CosRayDestination.Device.route) {
    val viewModel: DeviceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionsState =
            rememberMultiplePermissionsState(
                    permissions =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            ) {
                              listOf(
                                      android.Manifest.permission.BLUETOOTH_SCAN,
                                      android.Manifest.permission.BLUETOOTH_CONNECT,
                              )
                            } else {
                              listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            }
            )

    val authState by
            appState.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading)
    val isAuthenticated = authState is AuthState.Authenticated

    LaunchedEffect(permissionsState.allPermissionsGranted) {
      viewModel.onPermissionsChanged(permissionsState.allPermissionsGranted)
    }

    DeviceScreen(
            state = state,
            permissionsState = permissionsState,
            onRequestPermissions = {
              viewModel.onPermissionsChanged(permissionsState.allPermissionsGranted)
            },
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onNavigateToDashboard = { appState.navigateTo(CosRayDestination.Dashboard) },
            isAuthenticated = isAuthenticated,
            onRequestLogin = {
              appState.exitGuestMode()
              appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
            },
            onOpenDrawer = onOpenDrawer,
    )
  }
}

private fun NavGraphBuilder.dashboardDestination(onOpenDrawer: () -> Unit) {
  composable(CosRayDestination.Dashboard.route) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
            state = state,
            onMessageShown = viewModel::clearStatusMessage,
            onOpenDrawer = onOpenDrawer,
            onUploadClicked = viewModel::uploadBufferedTelemetry,
            onSendStatusClicked = viewModel::sendStatusCommand,
            onSendMuonClicked = viewModel::sendMuonStartCommand,
            onSendTimelineClicked = viewModel::sendTimelineStartCommand,
            onSendStopClicked = viewModel::sendStopCommand,
    )
  }
}

private fun NavGraphBuilder.apiTestDestination(onOpenDrawer: () -> Unit) {
  composable(CosRayDestination.ApiTest.route) {
    val viewModel: com.grid.cosrayapp.feature.apitest.ApiTestViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    com.grid.cosrayapp.feature.apitest.ApiTestScreen(
            state = state,
            onBaseUrlChange = viewModel::updateBaseUrl,
            onTokenChange = viewModel::updateToken,
            onUsernameChange = viewModel::updateUsername,
            onPasswordChange = viewModel::updatePassword,
            onMacAddressChange = viewModel::updateMacAddress,
            onDeviceNameChange = viewModel::updateDeviceName,
            onDeviceDescriptionChange = viewModel::updateDeviceDescription,
            onDeviceIdChange = viewModel::updateDeviceId,
            onTestLogin = viewModel::testLogin,
            onTestGetUserInfo = viewModel::testGetUserInfo,
            onTestListDevices = viewModel::testListDevices,
            onTestGetDevice = viewModel::testGetDevice,
            onTestCreateDevice = viewModel::testCreateDevice,
            onTestUpdateDevice = viewModel::testUpdateDevice,
            onTestDeleteDevice = viewModel::testDeleteDevice,
            onTestUploadMuonPacket = viewModel::testUploadMuonPacket,
            onTestUploadTimelinePacket = viewModel::testUploadTimelinePacket,
            onClearResponse = viewModel::clearResponse,
            onOpenDrawer = onOpenDrawer,
    )
  }
}

private fun NavGraphBuilder.settingsDestination(
        appState: CosRayAppState,
        onOpenDrawer: () -> Unit,
) {
  composable(CosRayDestination.Settings.route) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
            state = state,
            onLogout = {
              viewModel.logout()
              appState.exitGuestMode()
              appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
            },
            onToggleDarkTheme = viewModel::toggleDarkTheme,
            onToggleOledDark = viewModel::toggleOledDark,
            onOpenDrawer = onOpenDrawer,
    )
  }
}
