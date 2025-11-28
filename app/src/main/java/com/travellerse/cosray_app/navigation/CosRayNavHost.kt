package com.travellerse.cosray_app.navigation

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.travellerse.cosray_app.BuildConfig
import com.travellerse.cosray_app.R
import com.travellerse.cosray_app.core.ui.viewModelFactory
import com.travellerse.cosray_app.data.auth.AuthState
import com.travellerse.cosray_app.data.auth.AuthValidator
import com.travellerse.cosray_app.feature.auth.LoginScreen
import com.travellerse.cosray_app.feature.auth.LoginViewModel
import com.travellerse.cosray_app.feature.dashboard.DashboardScreen
import com.travellerse.cosray_app.feature.dashboard.DashboardViewModel
import com.travellerse.cosray_app.feature.device.DeviceScreen
import com.travellerse.cosray_app.feature.device.DeviceViewModel
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
      ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.75f)) {
        Spacer(Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.app_name),
          modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
          style = MaterialTheme.typography.headlineMedium,
        )
        NavigationDrawerItem(
          label = { Text(stringResource(R.string.device_scan_title)) },
          selected = currentDestination == CosRayDestination.Device.route,
          onClick = {
            scope.launch { drawerState.close() }
            appState.navigateTo(CosRayDestination.Device)
          },
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
          label = { Text(stringResource(R.string.device_dashboard_action)) },
          selected = currentDestination == CosRayDestination.Dashboard.route,
          onClick = {
            scope.launch { drawerState.close() }
            appState.navigateTo(CosRayDestination.Dashboard)
          },
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        // API Test menu (Debug only)
        if (BuildConfig.DEBUG) {
          NavigationDrawerItem(
            label = { Text(stringResource(R.string.api_test_title)) },
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
            label = { Text(stringResource(R.string.device_logout_action)) },
            selected = false,
            onClick = {
              scope.launch { drawerState.close() }
              // TODO: Implement logout callback if needed
              appState.exitGuestMode()
              appState.navigateTo(CosRayDestination.Login, popUpToStart = true)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
          )
        } else {
          NavigationDrawerItem(
            label = { Text(stringResource(R.string.device_login_action)) },
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
      deviceDestination(appState, onOpenDrawer = { scope.launch { drawerState.open() } })
      dashboardDestination(appState, onOpenDrawer = { scope.launch { drawerState.open() } })
      // API Test destination (Debug only)
      if (BuildConfig.DEBUG) {
        apiTestDestination(onOpenDrawer = { scope.launch { drawerState.open() } })
      }
    }
  }
}

private fun NavGraphBuilder.loginDestination(appState: CosRayAppState) {
  composable(CosRayDestination.Login.route) {
    val viewModel: LoginViewModel =
      viewModel(
        factory = viewModelFactory { LoginViewModel(appState.authRepository, AuthValidator) }
      )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
      state = state,
      onUsernameChange = viewModel::onUsernameChanged,
      onEmailChange = viewModel::onEmailChanged,
      onPasswordChange = viewModel::onPasswordChanged,
      onDisplayNameChange = viewModel::onDisplayNameChanged,
      onSubmit = viewModel::submit,
      onToggleMode = viewModel::toggleCreateAccount,
      onContinueAsGuest = {
        appState.enterGuestMode()
        appState.navigateTo(CosRayDestination.Device, popUpToStart = true)
      },
    )
  }
}

// MVVM Model - view model -view
@OptIn(ExperimentalPermissionsApi::class)
private fun NavGraphBuilder.deviceDestination(appState: CosRayAppState, onOpenDrawer: () -> Unit) {
  composable(CosRayDestination.Device.route) {
    val viewModel: DeviceViewModel =
      viewModel(
        factory =
          viewModelFactory {
            DeviceViewModel(
              bleRepository = appState.container.bleRepository,
              telemetryRepository = appState.container.telemetryRepository,
            )
          }
      )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionsState =
      rememberMultiplePermissionsState(
        permissions =
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

private fun NavGraphBuilder.dashboardDestination(
  appState: CosRayAppState,
  onOpenDrawer: () -> Unit,
) {
  composable(CosRayDestination.Dashboard.route) {
    val viewModel: DashboardViewModel =
      viewModel(
        factory =
          viewModelFactory {
            DashboardViewModel(
              telemetryRepository = appState.container.telemetryRepository,
              authRepository = appState.authRepository,
            )
          }
      )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
      state = state,
      onUpload = viewModel::uploadBufferedTelemetry,
      onMessageShown = viewModel::clearStatusMessage,
      onOpenDrawer = onOpenDrawer,
    )
  }
}

private fun NavGraphBuilder.apiTestDestination(onOpenDrawer: () -> Unit) {
  composable(CosRayDestination.ApiTest.route) {
    val viewModel: com.travellerse.cosray_app.feature.apitest.ApiTestViewModel =
      viewModel(
        factory = viewModelFactory { com.travellerse.cosray_app.feature.apitest.ApiTestViewModel() }
      )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    com.travellerse.cosray_app.feature.apitest.ApiTestScreen(
      state = state,
      onBaseUrlChange = viewModel::updateBaseUrl,
      onTokenChange = viewModel::updateToken,
      onUsernameChange = viewModel::updateUsername,
      onPasswordChange = viewModel::updatePassword,
      onEmailChange = viewModel::updateEmail,
      onDisplayNameChange = viewModel::updateDisplayName,
      onMacAddressChange = viewModel::updateMacAddress,
      onDeviceNameChange = viewModel::updateDeviceName,
      onDeviceDescriptionChange = viewModel::updateDeviceDescription,
      onDeviceIdChange = viewModel::updateDeviceId,
      onTestLogin = viewModel::testLogin,
      onTestRegister = viewModel::testRegister,
      onTestGetUserInfo = viewModel::testGetUserInfo,
      onTestRegisterDevice = viewModel::testRegisterDevice,
      onTestListDevices = viewModel::testListDevices,
      onTestGetDevice = viewModel::testGetDevice,
      onTestUpdateDevice = viewModel::testUpdateDevice,
      onTestDeleteDevice = viewModel::testDeleteDevice,
      onTestUploadMuonPacket = viewModel::testUploadMuonPacket,
      onTestUploadTimelinePacket = viewModel::testUploadTimelinePacket,
      onClearResponse = viewModel::clearResponse,
      onOpenDrawer = onOpenDrawer,
    )
  }
}
