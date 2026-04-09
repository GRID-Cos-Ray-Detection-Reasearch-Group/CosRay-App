@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.grid.cosrayapp.feature.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grid.cosrayapp.R
import com.grid.cosrayapp.data.telemetry.DatabaseInspectionSnapshot
import com.grid.cosrayapp.data.telemetry.DetectorDatabaseSummary
import com.grid.cosrayapp.data.telemetry.db.TelemetrySampleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseViewerScreen(
    state: DatabaseInspectionSnapshot,
    onOpenDrawer: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.database_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.dashboard_navigation_menu)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(
                    title = stringResource(R.string.database_viewer_pending_section),
                    subtitle = stringResource(R.string.database_viewer_detectors)
                ) {
                    if (state.pendingDetectorSummaries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.database_viewer_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.pendingDetectorSummaries.forEach { summary ->
                                DetectorSummaryItem(summary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.database_viewer_row_count, state.pendingTelemetryRowCount + state.pendingRawPacketRowCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.database_viewer_recent_rows) + " (Telemetry: ${state.pendingTelemetryRows.size} | Raw: ${state.pendingRawPacketRows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.pendingTelemetryRows.isEmpty() && state.pendingRawPacketRows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.database_viewer_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.pendingTelemetryRows.take(5).forEach { row ->
                                TelemetryRowItem(row)
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.database_viewer_history_section),
                    subtitle = stringResource(R.string.database_viewer_detectors)
                ) {
                    if (state.historyDetectorSummaries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.database_viewer_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.historyDetectorSummaries.forEach { summary ->
                                DetectorSummaryItem(summary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.database_viewer_row_count, state.historyTelemetryRowCount + state.historyRawPacketRowCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.database_viewer_recent_rows) + " (Telemetry: ${state.historyTelemetryRows.size} | Raw: ${state.historyRawPacketRows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.historyTelemetryRows.isEmpty() && state.historyRawPacketRows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.database_viewer_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.historyTelemetryRows.take(5).forEach { row ->
                                TelemetryRowItem(row)
                            }
                        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun DetectorSummaryItem(summary: DetectorDatabaseSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.database_viewer_detector_id, summary.detectorId),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.database_viewer_telemetry_count, summary.telemetryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.database_viewer_raw_count, summary.rawPacketCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryRowItem(row: TelemetrySampleEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.database_viewer_telemetry_row_id_detector,
                    row.telemetryId,
                    row.detectorId,
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.database_viewer_telemetry_row_type_particles,
                    row.packetType,
                    row.particleCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
