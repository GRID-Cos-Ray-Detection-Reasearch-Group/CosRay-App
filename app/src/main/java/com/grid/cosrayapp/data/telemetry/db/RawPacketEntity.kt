package com.grid.cosrayapp.data.telemetry.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "raw_packets",
  indices =
    [
      Index(
        value = ["detector_id", "received_at_epoch_millis"],
        orders = [Index.Order.ASC, Index.Order.DESC],
      ),
    ],
)
data class RawPacketEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  val id: Long = 0,
  @ColumnInfo(name = "detector_id") val detectorId: String,
  @ColumnInfo(name = "characteristic_id") val characteristicId: String,
  @ColumnInfo(name = "received_at_epoch_millis") val receivedAtEpochMillis: Long,
  @ColumnInfo(name = "data") val data: ByteArray,
)
