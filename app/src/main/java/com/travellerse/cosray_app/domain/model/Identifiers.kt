package com.travellerse.cosray_app.domain.model

import java.util.Locale

@JvmInline
value class DetectorId(val value: String) {
  init {
    require(value.isNotBlank()) { "DetectorId cannot be blank" }
  }

  override fun toString(): String = value

  companion object {
    fun random(): DetectorId = DetectorId(java.util.UUID.randomUUID().toString())
  }
}

@JvmInline
value class TelemetryId(val value: String) {
  init {
    require(value.isNotBlank()) { "TelemetryId cannot be blank" }
  }

  override fun toString(): String = value

  companion object {
    fun fromTimestamp(timestampMillis: Long, detectorId: DetectorId): TelemetryId {
      val seed = "%s:%d".format(Locale.US, detectorId.value, timestampMillis)
      return TelemetryId(java.util.UUID.nameUUIDFromBytes(seed.toByteArray()).toString())
    }
  }
}

@JvmInline
value class UserId(val value: String) {
  init {
    require(value.isNotBlank()) { "UserId cannot be blank" }
  }

  override fun toString(): String = value
}
