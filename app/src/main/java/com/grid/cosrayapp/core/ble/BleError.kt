package com.grid.cosrayapp.core.ble

import java.util.UUID

/** Structured BLE error types for better error handling */
sealed interface BleError {
  /** Required Bluetooth permissions not granted */
  data class PermissionDenied(val permissions: List<String>) : BleError

  /** Bluetooth adapter is disabled */
  data class BluetoothDisabled(val message: String = "Bluetooth is disabled") : BleError

  /** Connection attempt timed out */
  data class ConnectionTimeout(val deviceAddress: String) : BleError

  /** GATT operation failed with specific status code */
  data class GattError(val status: Int, val message: String) : BleError

  /** Required GATT service not found on device */
  data class ServiceNotFound(val serviceUuid: UUID) : BleError

  /** Required GATT characteristic not found */
  data class CharacteristicNotFound(val charUuid: UUID) : BleError

  /** Write operation failed */
  data class WriteFailure(val error: Throwable) : BleError

  /** Read operation failed */
  data class ReadFailure(val error: Throwable) : BleError

  /** Notification enable/disable failed */
  data class NotificationError(val error: Throwable) : BleError

  /** Unknown error */
  data class Unknown(val cause: Throwable) : BleError

  /** Get human-readable error message */
  fun getErrorMessage(): String =
    when (this) {
      is PermissionDenied -> "Missing permissions: ${permissions.joinToString()}"
      is BluetoothDisabled -> message
      is ConnectionTimeout -> "Connection timeout for device $deviceAddress"
      is GattError -> "$message (status: $status)"
      is ServiceNotFound -> "Service not found: $serviceUuid"
      is CharacteristicNotFound -> "Characteristic not found: $charUuid"
      is WriteFailure -> "Write failed: ${error.message}"
      is ReadFailure -> "Read failed: ${error.message}"
      is NotificationError -> "Notification error: ${error.message}"
      is Unknown -> "Unknown error: ${cause.message}"
    }
}
