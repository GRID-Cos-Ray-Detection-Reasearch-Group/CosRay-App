package com.grid.cosrayapp.core.ble

import java.util.UUID

object BleConfig {
  // Supported service UUIDs - add more as needed for different device types
  val SUPPORTED_SERVICE_UUIDS =
    listOf(
      // MuonDetector service (current firmware)
      UUID.fromString("08070605-0403-0201-efcd-ab8967452301"),
      // Nordic UART Service (for compatibility with other devices)
      UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
      // Add more UUIDs here as needed
    )

  // Default to first supported service for backward compatibility
  val SERVICE_UUID: UUID = SUPPORTED_SERVICE_UUIDS.first()

  // Characteristic UUIDs - these may vary per service, so consider making them configurable
  val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("09070605-0403-0201-efcd-ab8967452301")
  val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("01090706-0504-0302-01ef-cdab89674523")
  val CLIENT_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
