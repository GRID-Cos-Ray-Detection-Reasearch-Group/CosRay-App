package com.travellerse.cosray_app.feature.apitest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.travellerse.cosray_app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTestScreen(
  state: ApiTestUiState,
  onBaseUrlChange: (String) -> Unit,
  onTokenChange: (String) -> Unit,
  onUsernameChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onDisplayNameChange: (String) -> Unit,
  onMacAddressChange: (String) -> Unit,
  onDeviceNameChange: (String) -> Unit,
  onDeviceDescriptionChange: (String) -> Unit,
  onDeviceIdChange: (String) -> Unit,
  onTestLogin: () -> Unit,
  onTestRegister: () -> Unit,
  onTestGetUserInfo: () -> Unit,
  onTestRegisterDevice: () -> Unit,
  onTestListDevices: () -> Unit,
  onTestGetDevice: () -> Unit,
  onTestUpdateDevice: () -> Unit,
  onTestDeleteDevice: () -> Unit,
  onTestUploadMuonPacket: () -> Unit,
  onTestUploadTimelinePacket: () -> Unit,
  onClearResponse: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.api_test_title)) },
        navigationIcon = {
          IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
          }
        },
      )
    }
  ) { paddingValues ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Server Configuration Section
      ServerConfigSection(baseUrl = state.baseUrl, onBaseUrlChange = onBaseUrlChange)

      HorizontalDivider()

      // Token Section
      TokenSection(token = state.token, onTokenChange = onTokenChange)

      HorizontalDivider()

      // Authentication Test Section
      AuthTestSection(
        username = state.username,
        password = state.password,
        email = state.email,
        displayName = state.displayName,
        onUsernameChange = onUsernameChange,
        onPasswordChange = onPasswordChange,
        onEmailChange = onEmailChange,
        onDisplayNameChange = onDisplayNameChange,
        onTestLogin = onTestLogin,
        onTestRegister = onTestRegister,
        onTestGetUserInfo = onTestGetUserInfo,
        isLoading = state.isLoading,
      )

      HorizontalDivider()

      // Device Management Test Section
      DeviceTestSection(
        macAddress = state.macAddress,
        deviceName = state.deviceName,
        deviceDescription = state.deviceDescription,
        deviceId = state.deviceId,
        onMacAddressChange = onMacAddressChange,
        onDeviceNameChange = onDeviceNameChange,
        onDeviceDescriptionChange = onDeviceDescriptionChange,
        onDeviceIdChange = onDeviceIdChange,
        onTestRegisterDevice = onTestRegisterDevice,
        onTestListDevices = onTestListDevices,
        onTestGetDevice = onTestGetDevice,
        onTestUpdateDevice = onTestUpdateDevice,
        onTestDeleteDevice = onTestDeleteDevice,
        isLoading = state.isLoading,
      )

      HorizontalDivider()

      // Packet Upload Test Section
      PacketTestSection(
        macAddress = state.macAddress,
        onMacAddressChange = onMacAddressChange,
        onTestUploadMuonPacket = onTestUploadMuonPacket,
        onTestUploadTimelinePacket = onTestUploadTimelinePacket,
        isLoading = state.isLoading,
      )

      HorizontalDivider()

      // Response Section
      ResponseSection(response = state.response, error = state.error, onClear = onClearResponse)
    }
  }
}

@Composable
private fun ServerConfigSection(baseUrl: String, onBaseUrlChange: (String) -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(R.string.api_test_server_config),
        style = MaterialTheme.typography.titleMedium,
      )
      OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.api_test_base_url_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
    }
  }
}

@Composable
private fun TokenSection(token: String, onTokenChange: (String) -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(R.string.api_test_token_label),
        style = MaterialTheme.typography.titleMedium,
      )
      OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.api_test_token_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
    }
  }
}

@Composable
private fun AuthTestSection(
  username: String,
  password: String,
  email: String,
  displayName: String,
  onUsernameChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onDisplayNameChange: (String) -> Unit,
  onTestLogin: () -> Unit,
  onTestRegister: () -> Unit,
  onTestGetUserInfo: () -> Unit,
  isLoading: Boolean,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(R.string.api_test_auth_section),
        style = MaterialTheme.typography.titleMedium,
      )

      // Login fields
      Text(
        text = stringResource(R.string.api_test_login_subtitle),
        style = MaterialTheme.typography.labelLarge,
      )
      OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.auth_username_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.auth_password_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      Button(onClick = onTestLogin, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
        Text(stringResource(R.string.api_test_test_login))
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Register fields
      Text(
        text = stringResource(R.string.api_test_register_subtitle),
        style = MaterialTheme.typography.labelLarge,
      )
      OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.auth_email_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text(stringResource(R.string.auth_display_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      Button(onClick = onTestRegister, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
        Text(stringResource(R.string.api_test_test_register))
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Get user info
      Button(
        onClick = onTestGetUserInfo,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
      ) {
        Text(stringResource(R.string.api_test_test_get_user))
      }
    }
  }
}

@Composable
private fun DeviceTestSection(
  macAddress: String,
  deviceName: String,
  deviceDescription: String,
  deviceId: String,
  onMacAddressChange: (String) -> Unit,
  onDeviceNameChange: (String) -> Unit,
  onDeviceDescriptionChange: (String) -> Unit,
  onDeviceIdChange: (String) -> Unit,
  onTestRegisterDevice: () -> Unit,
  onTestListDevices: () -> Unit,
  onTestGetDevice: () -> Unit,
  onTestUpdateDevice: () -> Unit,
  onTestDeleteDevice: () -> Unit,
  isLoading: Boolean,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(R.string.api_test_device_section),
        style = MaterialTheme.typography.titleMedium,
      )

      OutlinedTextField(
        value = macAddress,
        onValueChange = onMacAddressChange,
        label = { Text(stringResource(R.string.api_test_mac_address_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
      )
      OutlinedTextField(
        value = deviceName,
        onValueChange = onDeviceNameChange,
        label = { Text(stringResource(R.string.api_test_device_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      OutlinedTextField(
        value = deviceDescription,
        onValueChange = onDeviceDescriptionChange,
        label = { Text(stringResource(R.string.api_test_device_description_label)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
      )
      Button(
        onClick = onTestRegisterDevice,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
      ) {
        Text(stringResource(R.string.api_test_test_register_device))
      }

      HorizontalDivider()

      Button(
        onClick = onTestListDevices,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
      ) {
        Text(stringResource(R.string.api_test_test_list_devices))
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = deviceId,
        onValueChange = onDeviceIdChange,
        label = { Text(stringResource(R.string.api_test_device_id_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("1") },
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTestGetDevice, modifier = Modifier.weight(1f), enabled = !isLoading) {
          Text(stringResource(R.string.api_test_test_get_device))
        }
        Button(onClick = onTestUpdateDevice, modifier = Modifier.weight(1f), enabled = !isLoading) {
          Text(stringResource(R.string.api_test_test_update_device))
        }
      }
      Button(
        onClick = onTestDeleteDevice,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(R.string.api_test_test_delete_device))
      }
    }
  }
}

@Composable
private fun PacketTestSection(
  macAddress: String,
  onMacAddressChange: (String) -> Unit,
  onTestUploadMuonPacket: () -> Unit,
  onTestUploadTimelinePacket: () -> Unit,
  isLoading: Boolean,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(R.string.api_test_packet_section),
        style = MaterialTheme.typography.titleMedium,
      )

      OutlinedTextField(
        value = macAddress,
        onValueChange = onMacAddressChange,
        label = { Text(stringResource(R.string.api_test_mac_address_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
      )

      Button(
        onClick = onTestUploadMuonPacket,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
      ) {
        Text(stringResource(R.string.api_test_test_upload_muon))
      }

      Button(
        onClick = onTestUploadTimelinePacket,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
      ) {
        Text(stringResource(R.string.api_test_test_upload_timeline))
      }
    }
  }
}

@Composable
private fun ResponseSection(response: String, error: String?, onClear: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      if (error != null) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
      } else {
        CardDefaults.cardColors()
      },
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = stringResource(R.string.api_test_response),
          style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.api_test_clear_button)) }
      }

      if (response.isNotBlank()) {
        SelectionContainer {
          Text(
            text = response,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 100.dp, max = 400.dp)
                .verticalScroll(rememberScrollState()),
          )
        }
      } else {
        Text(
          text = stringResource(R.string.api_test_no_response),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
