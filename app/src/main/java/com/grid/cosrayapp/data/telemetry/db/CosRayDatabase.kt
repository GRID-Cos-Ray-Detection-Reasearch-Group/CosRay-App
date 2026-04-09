package com.grid.cosrayapp.data.telemetry.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities = [TelemetrySampleEntity::class, RawPacketEntity::class],
  version = 3,
  exportSchema = false,
)
@TypeConverters(InstantConverters::class, PacketTypeConverters::class)
abstract class CosRayDatabase : RoomDatabase() {
  abstract fun telemetrySampleDao(): TelemetrySampleDao

  abstract fun rawPacketDao(): RawPacketDao
}
