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
        telemetrySampleDao.observeDetectorCounts(isUploaded = false),
        rawPacketDao.observeDetectorCounts(isUploaded = false),
        telemetrySampleDao.observeLatestForInspection(isUploaded = false, limit = limit)
      ) { a, b, c -> Triple(a, b, c) },
      combine(
        rawPacketDao.observeLatestForInspection(isUploaded = false, limit = limit),
        telemetrySampleDao.observeTelemetryRowCount(isUploaded = false),
        rawPacketDao.observeRawPacketRowCount(isUploaded = false)
      ) { a, b, c -> Triple(a, b, c) },
      combine(
        telemetrySampleDao.observeDetectorCounts(isUploaded = true),
        rawPacketDao.observeDetectorCounts(isUploaded = true),
        telemetrySampleDao.observeLatestForInspection(isUploaded = true, limit = limit)
      ) { a, b, c -> Triple(a, b, c) },
      combine(
        rawPacketDao.observeLatestForInspection(isUploaded = true, limit = limit),
        telemetrySampleDao.observeTelemetryRowCount(isUploaded = true),
        rawPacketDao.observeRawPacketRowCount(isUploaded = true)
      ) { a, b, c -> Triple(a, b, c) }
    ) { p1, p2, h1, h2 ->
      DatabaseInspectionSnapshot(
        pendingDetectorSummaries = mergeDetectorSummaries(telemetryCounts = p1.first, rawCounts = p1.second),
        pendingTelemetryRows = p1.third,
        pendingRawPacketRows = p2.first,
        pendingTelemetryRowCount = p2.second,
        pendingRawPacketRowCount = p2.third,
        historyDetectorSummaries = mergeDetectorSummaries(telemetryCounts = h1.first, rawCounts = h1.second),
        historyTelemetryRows = h1.third,
        historyRawPacketRows = h2.first,
        historyTelemetryRowCount = h2.second,
        historyRawPacketRowCount = h2.third,
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
  val pendingDetectorSummaries: List<DetectorDatabaseSummary> = emptyList(),
  val pendingTelemetryRows: List<TelemetrySampleEntity> = emptyList(),
  val pendingRawPacketRows: List<RawPacketEntity> = emptyList(),
  val pendingTelemetryRowCount: Int = 0,
  val pendingRawPacketRowCount: Int = 0,
  val historyDetectorSummaries: List<DetectorDatabaseSummary> = emptyList(),
  val historyTelemetryRows: List<TelemetrySampleEntity> = emptyList(),
  val historyRawPacketRows: List<RawPacketEntity> = emptyList(),
  val historyTelemetryRowCount: Int = 0,
  val historyRawPacketRowCount: Int = 0,
)

data class DetectorDatabaseSummary(
  val detectorId: String,
  val telemetryCount: Int,
  val rawPacketCount: Int,
)
