@file:Suppress("FunctionNaming")

package com.grid.cosrayapp.feature.detectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ui.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectorManagementScreen(
  state: DetectorManagementUiState,
  onMacAddressChanged: (String) -> Unit,
  onNameChanged: (String) -> Unit,
  onDescriptionChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  onRefresh: () -> Unit,
  onRequestLogin: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.detector_management_title)) },
        navigationIcon = {
          IconButton(onClick = onOpenDrawer) {
            Icon(
              imageVector = Icons.Default.Menu,
              contentDescription = stringResource(R.string.settings_navigation_menu),
            )
          }
        },
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier =
        Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        SectionCard(
          title = stringResource(R.string.detector_management_account_section),
          subtitle =
            if (state.isAuthenticated) {
              state.user?.displayName ?: state.user?.email.orEmpty()
            } else {
              stringResource(R.string.detector_management_guest_prompt)
            },
        ) {
          if (state.isAuthenticated) {
            Text(
              text = state.user?.email.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRefresh, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.detector_management_refresh))
            }
          } else {
            Button(onClick = onRequestLogin, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.device_login_action))
            }
          }
        }
      }

      item {
        SectionCard(
          title = stringResource(R.string.detector_management_bound_devices),
          subtitle = stringResource(R.string.detector_management_bound_devices_subtitle),
        ) {
          when {
            state.isLoading -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.auth_loading))
              }
            }
            state.devices.isEmpty() -> {
              Text(
                text = stringResource(R.string.detector_management_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            else -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.devices.forEach { device ->
                  DetectorListItem(device = device)
                }
              }
            }
          }
        }
      }

      item {
        SectionCard(
          title = stringResource(R.string.detector_management_register_title),
          subtitle = stringResource(R.string.detector_management_register_subtitle),
        ) {
          OutlinedTextField(
            value = state.macAddress,
            onValueChange = onMacAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.api_test_mac_address_label)) },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            enabled = state.isAuthenticated && !state.isSubmitting,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.api_test_device_name_label)) },
            enabled = state.isAuthenticated && !state.isSubmitting,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChanged,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text(stringResource(R.string.api_test_device_description_label)) },
            enabled = state.isAuthenticated && !state.isSubmitting,
          )
          state.statusMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = message.asString(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onSubmit,
            enabled = state.isAuthenticated && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (state.isSubmitting) {
              CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text(stringResource(R.string.detector_management_submit))
          }
        }
      }
    }
  }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      content()
    }
  }
}

@Composable
private fun DetectorListItem(device: ManagedDetector) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(
          text = stringResource(if (device.isActive) R.string.detector_management_status_active else R.string.detector_management_status_inactive),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Text(text = device.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      device.description?.takeIf { it.isNotBlank() }?.let {
        Text(text = it, style = MaterialTheme.typography.bodyMedium)
      }
      Text(
        text = stringResource(R.string.detector_management_owner, device.ownerUsername),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(R.string.detector_management_last_seen, device.lastSeenAt ?: "--"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
