@file:Suppress("MagicNumber")

package com.grid.cosrayapp.domain.model

import java.time.Instant

data class BleDevice(
  val id: DetectorId,
  val macAddress: String,
  val name: String?,
  val signal: SignalStrength,
  val lastSeen: Instant,
  val advertisedServices: List<String> = emptyList(),
  val firmware: FirmwareVersion? = null,
)

data class SignalStrength(val rssi: Int, val updatedAt: Instant) {
  val quality: SignalQuality
    get() =
      when {
        rssi >= -60 -> SignalQuality.EXCELLENT
        rssi >= -70 -> SignalQuality.GOOD
        rssi >= -80 -> SignalQuality.FAIR
        else -> SignalQuality.WEAK
      }
}

enum class SignalQuality {
  EXCELLENT,
  GOOD,
  FAIR,
  WEAK,
}

data class FirmwareVersion(val versionName: String, val buildMetadata: String? = null) {
  override fun toString(): String = buildMetadata?.let { "$versionName+$it" } ?: versionName
}
