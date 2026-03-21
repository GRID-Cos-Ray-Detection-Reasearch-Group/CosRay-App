package com.grid.cosrayapp.data.telemetry.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RawPacketDao {
  @Insert
  suspend fun insert(packet: RawPacketEntity)

  @Query(
    """
      SELECT *
      FROM raw_packets
      WHERE detector_id = :detectorId
      ORDER BY received_at_epoch_millis DESC, id DESC
      LIMIT :limit
    """
  )
  fun observeLatest(detectorId: String, limit: Int): Flow<List<RawPacketEntity>>

  @Query(
    """
      DELETE
      FROM raw_packets
      WHERE detector_id = :detectorId
        AND id < (
          SELECT MIN(id)
          FROM (
            SELECT id
            FROM raw_packets
            WHERE detector_id = :detectorId
            ORDER BY received_at_epoch_millis DESC, id DESC
            LIMIT :keepLatest
          )
        )
    """
  )
  suspend fun pruneToLatest(detectorId: String, keepLatest: Int)
}
