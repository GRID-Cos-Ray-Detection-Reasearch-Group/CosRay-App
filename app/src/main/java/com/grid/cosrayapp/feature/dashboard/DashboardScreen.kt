@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.grid.cosrayapp.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val DASHBOARD_HORIZONTAL_PADDING = 20.dp
private val DASHBOARD_SECTION_SPACING = 20.dp

// Height constraints for event/packet list composables
private val EVENT_LIST_MIN_HEIGHT = 220.dp
private val EVENT_LIST_MAX_HEIGHT = 420.dp
private const val MAX_VISIBLE_EVENTS = 100

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
                        Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription =
                                        stringResource(R.string.dashboard_navigation_menu),
                        )
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
                            .padding(horizontal = DASHBOARD_HORIZONTAL_PADDING, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(DASHBOARD_SECTION_SPACING),
    ) {
      DashboardSection(
              title = stringResource(R.string.dashboard_topbar_title),
              subtitle = stringResource(R.string.dashboard_section_overview_subtitle),
      ) {
        HeaderSection(
                state = state,
                onUploadClicked = onUploadClicked,
                onSendStatusClicked = onSendStatusClicked,
                onSendMuonClicked = onSendMuonClicked,
                onSendTimelineClicked = onSendTimelineClicked,
                onSendStopClicked = onSendStopClicked,
        )
      }

      DashboardSection(
              title = stringResource(R.string.dashboard_section_live_metrics),
              subtitle = stringResource(R.string.dashboard_section_live_metrics_subtitle),
      ) {
        LiveMetricsSection(
                location = state.deviceLocation,
                acceleration = state.deviceOrientation,
                sipm = state.sipmStatus,
        )
      }

      DashboardSection(
              title = stringResource(R.string.dashboard_packet_statistics),
              subtitle = stringResource(R.string.dashboard_section_packet_statistics_subtitle),
      ) {
        PacketStatsCard(stats = state.packetStats)
      }

      EventHistorySection(
              selectedTab = selectedTab,
              muonCount = state.muonEvents.size,
              timelineCount = state.timelineEvents.size,
              rawPacketCount = state.rawPackets.size,
              onTabSelected = { selectedTab = it },
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
private fun DashboardSection(
        title: String,
        subtitle: String? = null,
        content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
      )
      subtitle?.let {
        Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    content()
  }
}

@Composable
private fun EventHistorySection(
        selectedTab: Int,
        muonCount: Int,
        timelineCount: Int,
        rawPacketCount: Int,
        onTabSelected: (Int) -> Unit,
        content: @Composable () -> Unit,
) {
  val summaryText =
          when (selectedTab) {
            0 -> stringResource(R.string.dashboard_event_history_muon_summary, muonCount)
            1 ->
                    stringResource(
                            R.string.dashboard_event_history_timeline_summary,
                            timelineCount,
                    )
            else ->
                    stringResource(R.string.dashboard_event_history_raw_summary, rawPacketCount)
          }

  DashboardSection(
          title = stringResource(R.string.dashboard_section_event_history),
          subtitle = summaryText,
  ) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
          Tab(
                  selected = selectedTab == 0,
                  onClick = { onTabSelected(0) },
                  text = {
                    Text(stringResource(R.string.dashboard_muon_events_tab, muonCount))
                  },
          )
          Tab(
                  selected = selectedTab == 1,
                  onClick = { onTabSelected(1) },
                  text = {
                    Text(stringResource(R.string.dashboard_timeline_events_tab, timelineCount))
                  },
          )
          Tab(
                  selected = selectedTab == 2,
                  onClick = { onTabSelected(2) },
                  text = {
                    Text(stringResource(R.string.dashboard_raw_packets_tab, rawPacketCount))
                  },
          )
        }

        Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
          content()
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
  val connectionSummary =
          if (deviceName != null) {
            stringResource(R.string.dashboard_device_connected, deviceName)
          } else {
            stringResource(R.string.dashboard_hero_waiting_subtitle)
          }

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
                    text = stringResource(R.string.dashboard_detector_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                    text = deviceName ?: stringResource(R.string.dashboard_hero_waiting_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
            )
            Text(
                    text = connectionSummary,
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
              OverviewMetricCard(
                      icon = Icons.Default.Schedule,
                      label = stringResource(R.string.dashboard_last_packet_label),
                      value = formatLastPacketText(state.packetStats.lastPacketTime),
              )
              OverviewMetricCard(
                      icon = Icons.Default.BluetoothConnected,
                      label = stringResource(R.string.dashboard_buffered_events_label),
                      value = state.packetStats.totalEventCount.toString(),
              )
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OverviewMetricCard(
                      icon = Icons.Default.Schedule,
                      label = stringResource(R.string.dashboard_last_packet_label),
                      value = formatLastPacketText(state.packetStats.lastPacketTime),
                      modifier = Modifier.weight(1f),
              )
              OverviewMetricCard(
                      icon = Icons.Default.BluetoothConnected,
                      label = stringResource(R.string.dashboard_buffered_events_label),
                      value = state.packetStats.totalEventCount.toString(),
                      modifier = Modifier.weight(1f),
              )
            }
          }
        }

        Text(
                text = stringResource(R.string.dashboard_upload_supporting_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

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
        Text(
                text = stringResource(R.string.dashboard_command_supporting_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CommandButtonGrid(
                commandEnabled = commandEnabled,
                onSendStatusClicked = onSendStatusClicked,
                onSendMuonClicked = onSendMuonClicked,
                onSendTimelineClicked = onSendTimelineClicked,
                onSendStopClicked = onSendStopClicked,
        )

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
private fun OverviewMetricCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        value: String,
        modifier: Modifier = Modifier,
) {
  Surface(
          modifier = modifier,
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
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
private fun CommandButtonGrid(
        commandEnabled: Boolean,
        onSendStatusClicked: () -> Unit,
        onSendMuonClicked: () -> Unit,
        onSendTimelineClicked: () -> Unit,
        onSendStopClicked: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val compactLayout = maxWidth < 420.dp
    if (compactLayout) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
                onClick = onSendStatusClicked,
                enabled = commandEnabled,
                modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.dashboard_command_status)) }
        FilledTonalButton(
                onClick = onSendMuonClicked,
                enabled = commandEnabled,
                modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.dashboard_command_muon)) }
        FilledTonalButton(
                onClick = onSendTimelineClicked,
                enabled = commandEnabled,
                modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.dashboard_command_timeline)) }
        OutlinedButton(
                onClick = onSendStopClicked,
                enabled = commandEnabled,
                modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.dashboard_command_stop)) }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
      }
    }
  }
}

@Composable
private fun LiveMetricsSection(
        location: LocationSnapshot?,
        acceleration: AccelerationSnapshot?,
        sipm: SipmMonitoring?,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val wideLayout = maxWidth >= 520.dp
    if (wideLayout) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          location?.let { LocationCard(location = it, modifier = Modifier.weight(1f)) }
          acceleration?.let { OrientationCard(acceleration = it, modifier = Modifier.weight(1f)) }
        }
        sipm?.let { SipmMonitoringCard(sipm = it) }
      }
    } else {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        location?.let { LocationCard(location = it) }
        acceleration?.let { OrientationCard(acceleration = it) }
        sipm?.let { SipmMonitoringCard(sipm = it) }
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
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
              text = stringResource(R.string.dashboard_gps_location),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
      )
      if (location.isMeaningful()) {
        Text(
                text =
                        stringResource(
                                R.string.dashboard_location_coordinates_short,
                                location.latitudeDegrees,
                                location.longitudeDegrees,
                        ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
              text = stringResource(R.string.dashboard_orientation),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
      )
      if (acceleration.isMeaningful()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
              style = MaterialTheme.typography.labelSmall,
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
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
              text = stringResource(R.string.dashboard_sipm_monitoring),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HighlightMetric(
                label = stringResource(R.string.dashboard_sipm_voltage),
                value = stringResource(R.string.dashboard_sipm_voltage_value, sipm.voltageMillivolts),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        HighlightMetric(
                label = stringResource(R.string.dashboard_sipm_current),
                value = stringResource(R.string.dashboard_sipm_current_value, sipm.currentMicroamps),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
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
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactLayout = maxWidth < 420.dp
        if (compactLayout) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HighlightMetric(
                    label = stringResource(R.string.dashboard_muon_packets),
                    value = stats.muonPacketCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HighlightMetric(
                    label = stringResource(R.string.dashboard_timeline_packets),
                    value = stats.timelinePacketCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HighlightMetric(
                    label = stringResource(R.string.dashboard_total_events),
                    value = stats.totalEventCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        } else {
          Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            HighlightMetric(
                    label = stringResource(R.string.dashboard_muon_packets),
                    value = stats.muonPacketCount.toString(),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HighlightMetric(
                    label = stringResource(R.string.dashboard_timeline_packets),
                    value = stats.timelinePacketCount.toString(),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HighlightMetric(
                    label = stringResource(R.string.dashboard_total_events),
                    value = stats.totalEventCount.toString(),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      stats.averageEnergyAdcCounts?.let { avgEnergy ->
        MetricRow(
                label = stringResource(R.string.dashboard_average_energy, avgEnergy.toInt()),
                value = stringResource(R.string.dashboard_mean_signal_strength),
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      MetricRow(
              label = stringResource(R.string.dashboard_last_packet_label),
              value = formatLastPacketText(stats.lastPacketTime),
              valueColor = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun HighlightMetric(
        label: String,
        value: String,
        modifier: Modifier = Modifier,
        containerColor: Color,
        contentColor: Color,
) {
  Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = containerColor) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = contentColor,
      )
      Text(
              text = value,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = contentColor,
      )
    }
  }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
  Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.size(12.dp))
    Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
    )
  }
}

@Composable
private fun MuonEventsList(events: List<TelemetrySample>) {
  if (events.isEmpty()) {
    Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_muon_events),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT, max = EVENT_LIST_MAX_HEIGHT).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(
                                                        items = events.take(MAX_VISIBLE_EVENTS),
              key = { sample -> sample.id.value },
      ) { sample ->
        MuonEventCard(sample)
      }
    }
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
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
                text = stringResource(R.string.dashboard_muon_event_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_timeline_events),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT, max = EVENT_LIST_MAX_HEIGHT).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(
                                                        items = events.take(MAX_VISIBLE_EVENTS),
              key = { sample -> sample.id.value },
      ) { sample ->
        TimelineEventCard(sample)
      }
    }
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
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
              text = stringResource(R.string.dashboard_timeline_sample_label),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
              text = formatSampleInstant(sample.recordedAt),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
      )

      sample.environment.sipmTemperatureCelsius?.let { temp ->
        MetricRow(
                label = stringResource(R.string.dashboard_sipm_temp),
                value = stringResource(R.string.dashboard_temperature_value, temp),
        )
      }
      sample.environment.mcuTemperatureCelsius?.let { temp ->
        MetricRow(
                label = stringResource(R.string.dashboard_mcu_temp),
                value = stringResource(R.string.dashboard_temperature_value, temp),
        )
      }

      sample.location?.let { location ->
        if (location.isMeaningful()) {
          MetricRow(
                  label = stringResource(R.string.dashboard_gps_location),
                  value =
                          stringResource(
                                  R.string.dashboard_location_coords,
                                  location.latitudeDegrees,
                                  location.longitudeDegrees,
                                  location.altitudeMeters,
                          ),
          )
        }
      }

      sample.acceleration?.let { accel ->
        if (accel.isMeaningful()) {
          MetricRow(
                  label = stringResource(R.string.dashboard_orientation),
                  value =
                          stringResource(
                                  R.string.dashboard_acceleration_xyz,
                                  accel.xAxis,
                                  accel.yAxis,
                                  accel.zAxis,
                          ),
          )
        }
      }

      sample.packetMetadata?.let { metadata ->
        MetricRow(
                label = stringResource(R.string.dashboard_packet_label),
                value = stringResource(R.string.dashboard_packet_counter, metadata.packageCounter),
        )
      }
    }
  }
}

private fun formatInstant(instant: Instant): String = TIME_FORMATTER.format(instant)

private fun formatSampleInstant(instant: Instant): String =
        if (instant.toEpochMilli() <= 0L) {
          "--"
        } else {
          formatInstant(instant)
        }

@Composable
private fun formatLastPacketText(instant: Instant?): String =
        if (instant == null || instant.toEpochMilli() <= 0L) {
          stringResource(R.string.dashboard_waiting_for_data)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT),
            contentAlignment = Alignment.Center
    ) {
      Text(
              text = stringResource(R.string.dashboard_no_raw_packets),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = EVENT_LIST_MIN_HEIGHT, max = EVENT_LIST_MAX_HEIGHT).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(
              items = packets,
                                                        key = { packet -> packet.id },
      ) { packet ->
        RawPacketCard(packet)
      }
    }
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
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                text = stringResource(R.string.dashboard_packet_size_bytes, packet.data.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      val hexString = remember(packet.data) {
        packet.data.joinToString(" ") { String.format("%02X", it) }
      }
      Text(
              text = stringResource(R.string.dashboard_payload_label),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
