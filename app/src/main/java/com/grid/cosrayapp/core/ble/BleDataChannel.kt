package com.grid.cosrayapp.core.ble

import java.util.UUID
import kotlinx.coroutines.flow.SharedFlow

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
   * Equality based on characteristicId and data content only.
   * Timestamp is excluded to allow meaningful collection/diffing operations.
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

/**
 * Interface for BLE data communication channel.
 *
 * Handles sending commands and receiving notifications/indications from connected BLE devices.
 */
interface BleDataChannel {
  /**
   * Flow of incoming data packets from the connected device.
   *
   * Subscribers receive [RawPacket] instances whenever the device sends a notification or
   * indication.
   */
  val incomingData: SharedFlow<RawPacket>

  /**
   * Send a command to the connected device.
   *
   * @param command Byte array to send.
   * @return Result indicating success or failure.
   */
  suspend fun sendCommand(command: ByteArray): Result<Unit>

  /**
   * Enable notifications on the default notify characteristic.
   *
   * @return Result indicating success or failure.
   */
  suspend fun enableNotifications(): Result<Unit>

  /**
   * Disable notifications on the default notify characteristic.
   *
   * @return Result indicating success or failure.
   */
  suspend fun disableNotifications(): Result<Unit>
}
