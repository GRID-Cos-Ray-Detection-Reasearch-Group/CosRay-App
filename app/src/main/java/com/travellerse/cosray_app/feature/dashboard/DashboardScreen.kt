package com.travellerse.cosray_app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travellerse.cosray_app.R
import com.travellerse.cosray_app.core.ui.asString
import com.travellerse.cosray_app.domain.model.TelemetrySample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  state: DashboardUiState,
  onUpload: () -> Unit,
  onMessageShown: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }

  val uploadMessage = state.uploadMessage?.asString()
  LaunchedEffect(uploadMessage) {
    if (uploadMessage != null) {
      snackbarHostState.showSnackbar(uploadMessage)
      onMessageShown()
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.dashboard_title_default)) },
        navigationIcon = {
          IconButton(onClick = onOpenDrawer) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      HeaderSection(state)

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = stringResource(R.string.dashboard_live_data),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  state.device?.let {
                    val name = it.name ?: it.macAddress
                    stringResource(R.string.dashboard_device_connected, name)
                  } ?: stringResource(R.string.dashboard_device_disconnected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(onClick = onUpload, enabled = !state.isUploading) {
              Text(
                text =
                  if (state.isUploading) {
                    stringResource(R.string.dashboard_uploading)
                  } else {
                    stringResource(R.string.dashboard_upload)
                  }
              )
            }
          }

          Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            Text(
              text =
                if (state.samples.isEmpty()) {
                  stringResource(R.string.dashboard_no_data)
                } else {
                  stringResource(R.string.dashboard_live_data)
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Surface(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
      ) {
        LazyColumn(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (state.samples.isEmpty()) {
            item {
              Text(
                text = stringResource(R.string.dashboard_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(state.samples) { sample -> TelemetryCard(sample) }
          }
        }
      }
    }
  }
}

@Composable
private fun HeaderSection(state: DashboardUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = state.user?.displayName ?: stringResource(R.string.dashboard_title_default),
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text =
        state.device?.let {
          val name = it.name ?: it.macAddress
          stringResource(R.string.dashboard_device_connected, name)
        } ?: stringResource(R.string.dashboard_device_disconnected),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun TelemetryCard(sample: TelemetrySample) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 4.dp,
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = DATE_FORMAT.format(Date(sample.recordedAt.toEpochMilli())),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
      )
      MetricRow(
        label = stringResource(R.string.dashboard_particle_count),
        value = sample.acquisition.particleCount.toString(),
      )
      MetricRow(
        label = stringResource(R.string.dashboard_dose_rate),
        value =
          stringResource(
            R.string.dashboard_dose_rate_value,
            sample.radiation.doseRateMicrosievertsPerHour,
          ),
      )
      MetricRow(
        label = stringResource(R.string.dashboard_temperature),
        value =
          sample.environment.primaryTemperatureCelsius?.let {
            stringResource(R.string.dashboard_temperature_value, it)
          } ?: "--",
      )
      MetricRow(
        label = stringResource(R.string.dashboard_battery),
        value =
          sample.power.batteryPercent?.let { stringResource(R.string.dashboard_battery_value, it) }
            ?: "--",
      )
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

@Suppress("MagicNumber")
private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
