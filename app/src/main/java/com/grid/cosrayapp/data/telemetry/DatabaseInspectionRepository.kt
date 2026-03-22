package com.grid.cosrayapp.data.telemetry

import com.grid.cosrayapp.data.telemetry.db.DetectorRowCount
import com.grid.cosrayapp.data.telemetry.db.RawPacketDao
import com.grid.cosrayapp.data.telemetry.db.RawPacketEntity
import com.grid.cosrayapp.data.telemetry.db.TelemetrySampleDao
import com.grid.cosrayapp.data.telemetry.db.TelemetrySampleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DatabaseInspectionRepository(
  private val telemetrySampleDao: TelemetrySampleDao,
  private val rawPacketDao: RawPacketDao,
) {
  fun observeInspection(limit: Int): Flow<DatabaseInspectionSnapshot> =
    combine(
      combine(
        telemetrySampleDao.observeDetectorCounts(),
        rawPacketDao.observeDetectorCounts(),
        telemetrySampleDao.observeLatestForInspection(limit = limit)
      ) { a, b, c -> Triple(a, b, c) },
      combine(
        rawPacketDao.observeLatestForInspection(limit = limit),
        telemetrySampleDao.observeTelemetryRowCount(),
        rawPacketDao.observeRawPacketRowCount()
      ) { a, b, c -> Triple(a, b, c) }
    ) { first, second ->
      DatabaseInspectionSnapshot(
        detectorSummaries = mergeDetectorSummaries(telemetryCounts = first.first, rawCounts = first.second),
        telemetryRows = first.third,
        rawPacketRows = second.first,
        telemetryRowCount = second.second,
        rawPacketRowCount = second.third,
      )
    }

  private fun mergeDetectorSummaries(
    telemetryCounts: List<DetectorRowCount>,
    rawCounts: List<DetectorRowCount>,
  ): List<DetectorDatabaseSummary> {
    val telemetryByDetector = telemetryCounts.associateBy(DetectorRowCount::detectorId)
    val rawByDetector = rawCounts.associateBy(DetectorRowCount::detectorId)

    return (telemetryByDetector.keys + rawByDetector.keys)
      .toSortedSet()
      .map { detectorId ->
        DetectorDatabaseSummary(
          detectorId = detectorId,
          telemetryCount = telemetryByDetector[detectorId]?.rowCount ?: 0,
          rawPacketCount = rawByDetector[detectorId]?.rowCount ?: 0,
        )
      }
  }
}

data class DatabaseInspectionSnapshot(
  val detectorSummaries: List<DetectorDatabaseSummary> = emptyList(),
  val telemetryRows: List<TelemetrySampleEntity> = emptyList(),
  val rawPacketRows: List<RawPacketEntity> = emptyList(),
  val telemetryRowCount: Int = 0,
  val rawPacketRowCount: Int = 0,
)

data class DetectorDatabaseSummary(
  val detectorId: String,
  val telemetryCount: Int,
  val rawPacketCount: Int,
)
