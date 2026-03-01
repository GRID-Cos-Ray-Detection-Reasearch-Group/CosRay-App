package com.grid.cosrayapp.core.ble

import java.util.UUID

/**
 * Represents raw data received from a BLE characteristic.
 *
 * @property characteristicId UUID of the characteristic that sent the data.
 * @property data Raw byte data received.
 * @property timestamp System timestamp when data was received.
 */
data class RawPacket(
  val characteristicId: UUID,
  val data: ByteArray,
  val timestamp: Long = System.currentTimeMillis(),
) {
  /**
   * Equality based on characteristicId and data content only. Timestamp is excluded to allow
   * meaningful collection/diffing operations.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as RawPacket
    return characteristicId == other.characteristicId && data.contentEquals(other.data)
  }

  override fun hashCode(): Int {
    var result = characteristicId.hashCode()
    result = 31 * result + data.contentHashCode()
    return result
  }
}
