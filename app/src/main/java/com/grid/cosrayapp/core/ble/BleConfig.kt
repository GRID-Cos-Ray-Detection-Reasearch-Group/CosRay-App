package com.grid.cosrayapp.core.ble

import java.util.UUID

object BleConfig {
  // Default Nordic UART Service identifiers. Replace with detector-specific UUIDs when available.
  val SERVICE_UUID: UUID = UUID.fromString("08070605-0403-0201-efcd-ab8967452301")
  val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("09070605-0403-0201-efcd-ab8967452301")
  val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("01090706-0504-0302-01ef-cdab89674523")
  val CLIENT_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
