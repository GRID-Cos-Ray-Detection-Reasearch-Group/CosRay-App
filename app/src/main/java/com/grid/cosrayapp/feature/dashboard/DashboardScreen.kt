@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.grid.cosrayapp.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ble.RawPacket
import com.grid.cosrayapp.core.ui.asString
import com.grid.cosrayapp.domain.model.AccelerationSnapshot
import com.grid.cosrayapp.domain.model.LocationSnapshot
import com.grid.cosrayapp.domain.model.SipmMonitoring
import com.grid.cosrayapp.domain.model.TelemetrySample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Reusable DateTimeFormatter for raw packet display
private val RAW_PACKET_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

// Energy level thresholds for muon event classification (ADC counts)
private const val HIGH_ENERGY_THRESHOLD = 40000
private const val MEDIUM_ENERGY_THRESHOLD = 20000

// Reusable DateTimeFormatter for timestamp display
private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
        state: DashboardUiState,
        onMessageShown: () -> Unit,
        onOpenDrawer: () -> Unit,
        onUploadClicked: () -> Unit,
        onSendStatusClicked: () -> Unit,
        onSendMuonClicked: () -> Unit,
        onSendTimelineClicked: () -> Unit,
        onSendStopClicked: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTab by remember { mutableIntStateOf(0) }

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
                    title = { Text(stringResource(R.string.dashboard_topbar_title)) },
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
            modifier =
                    Modifier.fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header Section
      HeaderSection(
              state = state,
              onUploadClicked = onUploadClicked,
              onSendStatusClicked = onSendStatusClicked,
              onSendMuonClicked = onSendMuonClicked,
              onSendTimelineClicked = onSendTimelineClicked,
              onSendStopClicked = onSendStopClicked,
      )

      // Device Status Cards Row
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.deviceLocation?.let { location ->
          LocationCard(location = location, modifier = Modifier.weight(1f))
        }
        state.deviceOrientation?.let { accel ->
          OrientationCard(acceleration = accel, modifier = Modifier.weight(1f))
        }
      }

      // SiPM Status (if available)
      state.sipmStatus?.let { sipm -> SipmMonitoringCard(sipm) }

      // Packet Statistics
      PacketStatsCard(stats = state.packetStats)

      // Tab Selector for Muon / Timeline Events / Raw Packets
      SecondaryTabRow(selectedTabIndex = selectedTab) {
        Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                  Text(stringResource(R.string.dashboard_muon_events_tab, state.muonEvents.size))
                },
        )
        Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                  Text(
                          stringResource(
                                  R.string.dashboard_timeline_events_tab,
                                  state.timelineEvents.size
                          )
                  )
                },
        )
        Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                  Text(stringResource(R.string.dashboard_raw_packets_tab, state.rawPackets.size))
                },
        )
      }

      // Event List based on selected tab
      Surface(
              modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
              shape = RoundedCornerShape(16.dp),
              tonalElevation = 2.dp,
      ) {
        when (selectedTab) {
          0 -> MuonEventsList(events = state.muonEvents)
          1 -> TimelineEventsList(events = state.timelineEvents)
          2 -> RawPacketsList(packets = state.rawPackets)
        }
      }
    }
  }
}

@Composable
private fun HeaderSection(
        state: DashboardUiState,
        onUploadClicked: () -> Unit,
        onSendStatusClicked: () -> Unit,
        onSendMuonClicked: () -> Unit,
        onSendTimelineClicked: () -> Unit,
        onSendStopClicked: () -> Unit,
) {
  val deviceName = state.device?.name ?: state.device?.macAddress
  val commandEnabled = state.device != null && !state.isSendingCommand

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
    ) {
      Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                    text = deviceName ?: stringResource(R.string.dashboard_hero_waiting_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
            )
            Text(
                    text =
                            if (deviceName != null) {
                              stringResource(R.string.dashboard_device_connected, deviceName)
                            } else {
                              stringResource(R.string.dashboard_hero_waiting_subtitle)
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }

          StatusBadge(
                  connected = state.device != null,
                  text =
                          if (state.device != null) {
                            stringResource(R.string.dashboard_status_connected)
                          } else {
                            stringResource(R.string.dashboard_status_waiting)
                          },
          )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val compactLayout = maxWidth < 420.dp
          if (compactLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SummaryPill(
                      icon = Icons.Default.Schedule,
                      label = stringResource(R.string.dashboard_last_packet_label),
                      value = formatLastPacketText(state.packetStats.lastPacketTime),
              )
              SummaryPill(
                      icon = Icons.Default.BluetoothConnected,
                      label = stringResource(R.string.dashboard_buffered_events_label),
                      value = state.packetStats.totalEventCount.toString(),
              )
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SummaryPill(
                      icon = Icons.Default.Schedule,
                      label = stringResource(R.string.dashboard_last_packet_label),
                      value = formatLastPacketText(state.packetStats.lastPacketTime),
                      modifier = Modifier.weight(1f),
              )
              SummaryPill(
                      icon = Icons.Default.BluetoothConnected,
                      label = stringResource(R.string.dashboard_buffered_events_label),
                      value = state.packetStats.totalEventCount.toString(),
                      modifier = Modifier.weight(1f),
              )
            }
          }
        }

        Button(
                onClick = onUploadClicked,
                enabled = !state.isUploading,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
          if (state.isUploading) {
            androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
            )
          } else {
            Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
            )
          }
          Text(stringResource(R.string.dashboard_upload_button))
        }
      }
    }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
    ) {
      Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
                text = stringResource(R.string.dashboard_command_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
        )

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
                  onClick = onSendStatusClicked,
                  enabled = commandEnabled,
                  modifier = Modifier.weight(1f),
          ) { Text(stringResource(R.string.dashboard_command_status)) }
          FilledTonalButton(
                  onClick = onSendMuonClicked,
                  enabled = commandEnabled,
                  modifier = Modifier.weight(1f),
          ) { Text(stringResource(R.string.dashboard_command_muon)) }
        }
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
                  onClick = onSendTimelineClicked,
                  enabled = commandEnabled,
                  modifier = Modifier.weight(1f),
          ) { Text(stringResource(R.string.dashboard_command_timeline)) }
          OutlinedButton(
                  onClick = onSendStopClicked,
                  enabled = commandEnabled,
                  modifier = Modifier.weight(1f),
          ) { Text(stringResource(R.string.dashboard_command_stop)) }
        }

        Text(
                text =
                        if (state.isSendingCommand) {
                          stringResource(R.string.dashboard_command_sending)
                        } else {
                          stringResource(R.string.dashboard_command_hint)
                        },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun StatusBadge(connected: Boolean, text: String) {
  Surface(
          shape = RoundedCornerShape(999.dp),
          color =
                  if (connected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant
                  },
  ) {
    Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color =
                    if (connected) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
            fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun SummaryPill(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        value: String,
        modifier: Modifier = Modifier,
) {
  Surface(
          modifier = modifier,
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
  ) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
private fun LocationCard(location: LocationSnapshot, modifier: Modifier = Modifier) {
  androidx.compose.material3.Card(
          modifier = modifier,
          shape = RoundedCornerShape(16.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceVariant
                  ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
              text = stringResource(R.string.dashboard_gps_location),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
      )
      if (location.isMeaningful()) {
        Text(
                text = "%.6f°, %.6f°".format(location.latitudeDegrees, location.longitudeDegrees),
                style = MaterialTheme.typography.bodySmall,
        )
        Text(
                text = stringResource(R.string.dashboard_altitude_meters, location.altitudeMeters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
                text = stringResource(R.string.dashboard_location_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun OrientationCard(acceleration: AccelerationSnapshot, modifier: Modifier = Modifier) {
  androidx.compose.material3.Card(
          modifier = modifier,
          shape = RoundedCornerShape(16.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceVariant
                  ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
              text = stringResource(R.string.dashboard_orientation),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
      )
      if (acceleration.isMeaningful()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AxisIndicator("X", acceleration.xAxis)
          AxisIndicator("Y", acceleration.yAxis)
          AxisIndicator("Z", acceleration.zAxis)
        }
      } else {
        Text(
                text = stringResource(R.string.dashboard_orientation_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AxisIndicator(label: String, value: Int) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
            modifier =
                    Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(
                                    when {
                                      abs(value) > 100 -> MaterialTheme.colorScheme.error
                                      abs(value) > 50 -> MaterialTheme.colorScheme.tertiary
                                      else -> MaterialTheme.colorScheme.secondary
                                    }
                            ),
            contentAlignment = Alignment.Center,
    ) {
      Text(
              text = value.toString(),
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun SipmMonitoringCard(sipm: SipmMonitoring) {
  androidx.compose.material3.Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.primaryContainer
                  ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
              text = stringResource(R.string.dashboard_sipm_monitoring),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Text(
                  text = stringResource(R.string.dashboard_sipm_voltage),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
                  text =
                          stringResource(
                                  R.string.dashboard_sipm_voltage_value,
                                  sipm.voltageMillivolts
                          ),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
          )
        }
        Column {
          Text(
                  text = stringResource(R.string.dashboard_sipm_current),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
                  text =
                          stringResource(
                                  R.string.dashboard_sipm_current_value,
                                  sipm.currentMicroamps
                          ),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

@Composable
private fun PacketStatsCard(stats: PacketStatistics) {
  androidx.compose.material3.Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.secondaryContainer
                  ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
              text = stringResource(R.string.dashboard_packet_statistics),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(
                label = stringResource(R.string.dashboard_muon_packets),
                value = stats.muonPacketCount.toString(),
        )
        StatItem(
                label = stringResource(R.string.dashboard_timeline_packets),
                value = stats.timelinePacketCount.toString(),
        )
        StatItem(
                label = stringResource(R.string.dashboard_total_events),
                value = stats.totalEventCount.toString(),
        )
      }
      stats.averageEnergyAdcCounts?.let { avgEnergy ->
        Text(
                text = stringResource(R.string.dashboard_average_energy, avgEnergy.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      Text(
              text =
                      stringResource(
                              R.string.dashboard_last_packet_summary,
                              formatLastPacketText(stats.lastPacketTime),
                      ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun StatItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
private fun MuonEventsList(events: List<TelemetrySample>) {
  if (events.isEmpty()) {
    Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_muon_events),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { events.forEach { sample -> MuonEventCard(sample) } }
  }
}

@Composable
private fun MuonEventCard(sample: TelemetrySample) {
  val energy = sample.acquisition.particleCount
  val energyLevel =
          when {
            energy > HIGH_ENERGY_THRESHOLD -> EnergyLevel.HIGH
            energy > MEDIUM_ENERGY_THRESHOLD -> EnergyLevel.MEDIUM
            else -> EnergyLevel.LOW
          }

  androidx.compose.material3.Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor =
                                  when (energyLevel) {
                                    EnergyLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                                    EnergyLevel.MEDIUM ->
                                            MaterialTheme.colorScheme.tertiaryContainer
                                    EnergyLevel.LOW -> MaterialTheme.colorScheme.surfaceVariant
                                  }
                  ),
  ) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
                text = formatInstant(sample.recordedAt),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
        )
        sample.packetMetadata?.let { metadata ->
          Text(
                  text =
                          stringResource(
                                  R.string.dashboard_event_index,
                                  metadata.eventIndex ?: 0,
                                  metadata.eventCount ?: 0,
                          ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
                text = stringResource(R.string.dashboard_energy_adc, energy),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
        )
        Text(
                text =
                        stringResource(
                                R.string.dashboard_dose_rate_value,
                                sample.radiation.doseRateMicrosievertsPerHour,
                        ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TimelineEventsList(events: List<TelemetrySample>) {
  if (events.isEmpty()) {
    Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_timeline_events),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { events.forEach { sample -> TimelineEventCard(sample) } }
  }
}

@Composable
private fun TimelineEventCard(sample: TelemetrySample) {
  androidx.compose.material3.Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceVariant
                  ),
  ) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
              text = formatSampleInstant(sample.recordedAt),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
      )

      // Environment Data
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        sample.environment.sipmTemperatureCelsius?.let { temp ->
          MetricItem(
                  label = stringResource(R.string.dashboard_sipm_temp),
                  value = stringResource(R.string.dashboard_temperature_value, temp),
          )
        }
        sample.environment.mcuTemperatureCelsius?.let { temp ->
          MetricItem(
                  label = stringResource(R.string.dashboard_mcu_temp),
                  value = stringResource(R.string.dashboard_temperature_value, temp),
          )
        }
      }

      // Location Data
      sample.location?.let { location ->
        if (location.isMeaningful()) {
          Text(
                  text =
                          stringResource(
                                  R.string.dashboard_location_coords,
                                  location.latitudeDegrees,
                                  location.longitudeDegrees,
                                  location.altitudeMeters,
                          ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Acceleration Data
      sample.acceleration?.let { accel ->
        if (accel.isMeaningful()) {
          Text(
                  text =
                          stringResource(
                                  R.string.dashboard_acceleration_xyz,
                                  accel.xAxis,
                                  accel.yAxis,
                                  accel.zAxis,
                          ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Packet Metadata
      sample.packetMetadata?.let { metadata ->
        Text(
                text = stringResource(R.string.dashboard_packet_counter, metadata.packageCounter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun MetricItem(label: String, value: String) {
  Column {
    Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

private fun formatInstant(instant: Instant): String = TIME_FORMATTER.format(instant)

private fun formatSampleInstant(instant: Instant): String =
        if (instant.toEpochMilli() <= 0L) {
          "--"
        } else {
          formatInstant(instant)
        }

private fun formatLastPacketText(instant: Instant?): String =
        if (instant == null || instant.toEpochMilli() <= 0L) {
          "等待数据"
        } else {
          formatInstant(instant)
        }

private fun LocationSnapshot.isMeaningful(): Boolean =
        latitudeDegrees != 0.0 || longitudeDegrees != 0.0 || altitudeMeters != 0

private fun AccelerationSnapshot.isMeaningful(): Boolean = xAxis != 0 || yAxis != 0 || zAxis != 0

@Composable
private fun RawPacketsList(packets: List<RawPacket>) {
  if (packets.isEmpty()) {
    Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_raw_packets),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { packets.forEach { packet -> RawPacketCard(packet) } }
  }
}

@Composable
private fun RawPacketCard(packet: RawPacket) {
  androidx.compose.material3.Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceVariant
                  ),
  ) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val instant = Instant.ofEpochMilli(packet.timestamp)
        Text(
                text =
                        stringResource(
                                R.string.dashboard_raw_packet_time,
                                RAW_PACKET_TIME_FORMATTER.format(instant)
                        ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
        )
        Text(
                text = "${packet.data.size} bytes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      val hexString = packet.data.joinToString(" ") { String.format("%02X", it) }
      Text(
              text = hexString,
              style =
                      MaterialTheme.typography.bodySmall.copy(
                              fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                      ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private enum class EnergyLevel {
  LOW,
  MEDIUM,
  HIGH,
}
