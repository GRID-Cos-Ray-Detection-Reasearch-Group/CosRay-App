package com.grid.cosrayapp.data.telemetry.db

import androidx.room.TypeConverter
import com.grid.cosrayapp.domain.model.PacketType

class PacketTypeConverters {
  @TypeConverter
  fun fromPacketType(value: PacketType?): String? = value?.name

  @TypeConverter
  fun toPacketType(value: String?): PacketType? = value?.let(PacketType::valueOf)
}
