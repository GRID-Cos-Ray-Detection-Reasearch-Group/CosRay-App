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

  @Query("UPDATE raw_packets SET is_uploaded = 1 WHERE is_uploaded = 0 AND received_at_epoch_millis <= :maxTimestamp")
  suspend fun markAsUploaded(maxTimestamp: Long)

  @Query(
    """
      DELETE
      FROM raw_packets
      WHERE detector_id = :detectorId
        AND is_uploaded = 1
        AND id NOT IN (
          SELECT id
          FROM raw_packets
          WHERE detector_id = :detectorId
            AND is_uploaded = 1
          ORDER BY received_at_epoch_millis DESC, id DESC
          LIMIT :keepLatest
        )
    """
  )
  suspend fun pruneUploaded(detectorId: String, keepLatest: Int)

  @Query("SELECT COUNT(*) FROM raw_packets WHERE is_uploaded = :isUploaded")
  fun observeRawPacketRowCount(isUploaded: Boolean): Flow<Int>

  @Query(
    """
      SELECT detector_id AS detectorId, COUNT(*) AS rowCount
      FROM raw_packets
      WHERE is_uploaded = :isUploaded
      GROUP BY detector_id
      ORDER BY detector_id ASC
    """
  )
  fun observeDetectorCounts(isUploaded: Boolean): Flow<List<DetectorRowCount>>

  @Query(
    """
      SELECT *
      FROM raw_packets
      WHERE is_uploaded = :isUploaded
      ORDER BY received_at_epoch_millis DESC, id DESC
      LIMIT :limit
    """
  )
  fun observeLatestForInspection(isUploaded: Boolean, limit: Int): Flow<List<RawPacketEntity>>
}
