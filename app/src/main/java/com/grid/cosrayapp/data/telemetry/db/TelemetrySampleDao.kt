package com.grid.cosrayapp.data.telemetry.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.cosrayapp.domain.model.PacketType
import kotlinx.coroutines.flow.Flow

data class DetectorRowCount(
  val detectorId: String,
  val rowCount: Int,
)

@Dao
interface TelemetrySampleDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(samples: List<TelemetrySampleEntity>)

  @Query(
    """
      SELECT *
      FROM telemetry_samples
      WHERE detector_id = :detectorId
      ORDER BY recorded_at_epoch_millis DESC
      LIMIT :limit
    """
  )
  fun observeLatest(detectorId: String, limit: Int): Flow<List<TelemetrySampleEntity>>

  @Query(
    """
      SELECT *
      FROM telemetry_samples
      WHERE detector_id = :detectorId
        AND packet_type = :packetType
      ORDER BY recorded_at_epoch_millis DESC
      LIMIT :limit
    """
  )
  fun observeLatestByPacketType(
    detectorId: String,
    packetType: PacketType,
    limit: Int,
  ): Flow<List<TelemetrySampleEntity>>

  @Query("UPDATE telemetry_samples SET is_uploaded = 1 WHERE is_uploaded = 0 AND recorded_at_epoch_millis <= :maxTimestamp")
  suspend fun markAsUploaded(maxTimestamp: Long)

  @Query(
    """
      DELETE
      FROM telemetry_samples
      WHERE detector_id = :detectorId
        AND is_uploaded = 1
        AND telemetry_id NOT IN (
          SELECT telemetry_id
          FROM telemetry_samples
          WHERE detector_id = :detectorId
            AND is_uploaded = 1
          ORDER BY recorded_at_epoch_millis DESC, telemetry_id DESC
          LIMIT :keepLatest
        )
    """
  )
  suspend fun pruneUploaded(detectorId: String, keepLatest: Int)

  @Query("SELECT COUNT(*) FROM telemetry_samples WHERE is_uploaded = :isUploaded")
  fun observeTelemetryRowCount(isUploaded: Boolean): Flow<Int>

  @Query(
    """
      SELECT detector_id AS detectorId, COUNT(*) AS rowCount
      FROM telemetry_samples
      WHERE is_uploaded = :isUploaded
      GROUP BY detector_id
      ORDER BY detector_id ASC
    """
  )
  fun observeDetectorCounts(isUploaded: Boolean): Flow<List<DetectorRowCount>>

  @Query(
    """
      SELECT *
      FROM telemetry_samples
      WHERE is_uploaded = :isUploaded
      ORDER BY recorded_at_epoch_millis DESC
      LIMIT :limit
    """
  )
  fun observeLatestForInspection(isUploaded: Boolean, limit: Int): Flow<List<TelemetrySampleEntity>>
}
