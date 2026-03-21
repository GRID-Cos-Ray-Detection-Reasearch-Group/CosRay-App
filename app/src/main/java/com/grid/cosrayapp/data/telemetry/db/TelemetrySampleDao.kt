package com.grid.cosrayapp.data.telemetry.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.cosrayapp.domain.model.PacketType
import kotlinx.coroutines.flow.Flow

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

  @Query(
    """
      DELETE
      FROM telemetry_samples
      WHERE detector_id = :detectorId
        AND recorded_at_epoch_millis < (
          SELECT MIN(recorded_at_epoch_millis)
          FROM (
            SELECT recorded_at_epoch_millis
            FROM telemetry_samples
            WHERE detector_id = :detectorId
            ORDER BY recorded_at_epoch_millis DESC
            LIMIT :keepLatest
          )
        )
    """
  )
  suspend fun pruneToLatest(detectorId: String, keepLatest: Int)
}
