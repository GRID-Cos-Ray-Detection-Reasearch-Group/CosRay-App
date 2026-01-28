package com.grid.cosrayapp.core.ble

import java.time.Instant
import java.util.UUID

/** Represents the current state of BLE scanning. */
sealed class ScanState {
  /** No scan is in progress. */
  data object Idle : ScanState()

  /** Actively scanning for devices. */
  data object Scanning : ScanState()

  /** Scan failed with an error. */
  data class Failed(val error: BleError) : ScanState()
}

/**
 * Represents a discovered BLE device during scanning.
 *
 * This is a lightweight data class focused on scan-time information, separate from the domain
 * [BleDevice] model.
 */
data class DiscoveredDevice(
  /** MAC address of the device. */
  val address: String,
  /** Advertised device name, if available. */
  val name: String?,
  /** Signal strength in dBm. */
  val rssi: Int,
  /** Derived detector ID, if identifiable. */
  val detectorId: String?,
  /** Raw scan record bytes, if available. */
  val scanRecord: ByteArray?,
  /** Advertised service UUIDs. */
  val advertisedServices: List<UUID> = emptyList(),
  /** Timestamp when this device was last seen. */
  val lastSeen: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as DiscoveredDevice
    return address == other.address
  }

  override fun hashCode(): Int = address.hashCode()
}
