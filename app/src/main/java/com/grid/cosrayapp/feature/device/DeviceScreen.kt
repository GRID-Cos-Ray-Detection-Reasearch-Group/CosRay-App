package com.grid.cosrayapp.feature.device

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.domain.model.SignalQuality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
  state: DeviceUiState,
  permissionsState: MultiplePermissionsState,
  onRequestPermissions: () -> Unit,
  onStartScan: () -> Unit,
  onStopScan: () -> Unit,
  onConnect: (String) -> Unit,
  onDisconnect: () -> Unit,
  onNavigateToDashboard: () -> Unit,
  isAuthenticated: Boolean,
  onRequestLogin: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
  LaunchedEffect(permissionsState.allPermissionsGranted) { onRequestPermissions() }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.device_scan_title)) },
        navigationIcon = {
          IconButton(onClick = onOpenDrawer) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.device_scan_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      ConnectionOverview(
        state = state,
        onDisconnect = onDisconnect,
        onNavigateToDashboard = onNavigateToDashboard,
      )

      Button(
        onClick = {
          if (state.isScanning) {
            onStopScan()
          } else {
            // Request permissions if not granted, then
            // start scan
            if (!permissionsState.allPermissionsGranted) {
              permissionsState.launchMultiplePermissionRequest()
            } else {
              onStartScan()
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text =
            if (state.isScanning) {
              stringResource(R.string.device_scan_stop)
            } else {
              stringResource(R.string.device_scan_start)
            }
        )
      }

      if (!permissionsState.allPermissionsGranted) {
        Text(
          text = stringResource(R.string.device_permissions_missing),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(state.devices) { device ->
          val connectedDevice = (state.connectionState as? BleConnectionState.Connected)?.device
          val isConnected = connectedDevice?.macAddress == device.macAddress
          DeviceItemCard(
            item = device,
            isConnected = isConnected,
            onClick = {
              if (isConnected) {
                onDisconnect()
              } else {
                onConnect(device.macAddress)
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ConnectionOverview(
  state: DeviceUiState,
  onDisconnect: () -> Unit,
  onNavigateToDashboard: () -> Unit,
) {
  val (statusText, statusDescription, showActions) =
    when (val connection = state.connectionState) {
      is BleConnectionState.Connected -> {
        val deviceName = connection.device.name ?: connection.device.macAddress
        Triple(
          stringResource(R.string.device_status_connected, deviceName),
          stringResource(R.string.device_status_connected_detail),
          true,
        )
      }

      is BleConnectionState.Connecting -> {
        val deviceName = connection.device.name ?: connection.device.macAddress
        Triple(
          stringResource(R.string.device_status_connecting, deviceName),
          stringResource(R.string.device_status_connecting_detail),
          false,
        )
      }

      is BleConnectionState.DiscoveringServices -> {
        val deviceName = connection.device.name ?: connection.device.macAddress
        Triple("Discovering services: $deviceName", "Enumerating GATT services...", false)
      }

      is BleConnectionState.Disconnecting -> {
        val deviceName = connection.device.name ?: connection.device.macAddress
        Triple("Disconnecting: $deviceName", "Closing connection...", false)
      }

      is BleConnectionState.Failed -> {
        Triple(
          connection.error.getErrorMessage(),
          stringResource(R.string.device_status_retry_hint),
          false,
        )
      }

      is BleConnectionState.ScanFailed -> {
        Triple(connection.error.getErrorMessage(), "Scan failed. Try again.", false)
      }

      BleConnectionState.Idle -> {
        Triple(
          stringResource(R.string.device_status_disconnected),
          "Ready to scan for devices",
          false,
        )
      }

      BleConnectionState.Scanning -> {
        Triple("Scanning...", "Searching for BLE devices nearby", false)
      }

      BleConnectionState.Disconnected -> {
        Triple(
          stringResource(R.string.device_status_disconnected),
          stringResource(R.string.device_status_disconnected_detail),
          false,
        )
      }
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    tonalElevation = 6.dp,
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = stringResource(R.string.device_status_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (showActions) {
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = onNavigateToDashboard) {
              Text(stringResource(R.string.device_dashboard_action))
            }
            TextButton(onClick = onDisconnect) {
              Text(
                text = stringResource(R.string.device_disconnect_action),
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }

      Text(
        text = statusDescription,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      state.latestTelemetry?.let { sample ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          MetricRow(
            label = stringResource(R.string.device_metric_particles),
            value = sample.acquisition.particleCount.toString(),
          )
          MetricRow(
            label = stringResource(R.string.device_metric_dose_rate),
            value =
              stringResource(
                R.string.device_metric_dose_rate_value,
                sample.radiation.doseRateMicrosievertsPerHour,
              ),
          )
          MetricRow(
            label = stringResource(R.string.device_metric_board_temperature),
            value =
              sample.environment.primaryTemperatureCelsius?.let {
                stringResource(R.string.device_metric_board_temperature_value, it)
              } ?: "--",
          )
          MetricRow(
            label = stringResource(R.string.device_metric_battery),
            value =
              sample.power.batteryPercent?.let {
                stringResource(R.string.device_metric_battery_value, it)
              } ?: "--",
          )
          MetricRow(
            label = stringResource(R.string.device_metric_time),
            value = DATE_FORMAT.format(Date(sample.recordedAt.toEpochMilli())),
          )
        }
      }
    }
  }
}

@Composable
private fun MetricRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun DeviceItemCard(item: DeviceItem, isConnected: Boolean, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().clickable { onClick() },
    shape = RoundedCornerShape(20.dp),
    tonalElevation = 3.dp,
    color = MaterialTheme.colorScheme.surface,
  ) {
    Row(
      modifier = Modifier.padding(vertical = 16.dp, horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val deviceName = item.name ?: stringResource(R.string.device_name_unknown)
        Text(text = deviceName, style = MaterialTheme.typography.titleMedium)
        Text(
          text = item.macAddress,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = stringResource(R.string.device_rssi_format, item.rssi),
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          text = signalQualityLabel(item.signalQuality),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            stringResource(
              if (isConnected) {
                R.string.device_list_item_connected
              } else {
                R.string.device_list_item_available
              }
            ),
          style = MaterialTheme.typography.labelLarge,
          color =
            if (isConnected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
    }
  }
}

@Suppress("MagicNumber") private val DATE_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun signalQualityLabel(quality: SignalQuality): String =
  when (quality) {
    SignalQuality.EXCELLENT -> stringResource(R.string.device_signal_quality_excellent)
    SignalQuality.GOOD -> stringResource(R.string.device_signal_quality_good)
    SignalQuality.FAIR -> stringResource(R.string.device_signal_quality_fair)
    SignalQuality.WEAK -> stringResource(R.string.device_signal_quality_weak)
  }
