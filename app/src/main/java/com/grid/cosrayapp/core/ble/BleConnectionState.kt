package com.grid.cosrayapp.core.ble

import android.bluetooth.BluetoothGattService
import com.grid.cosrayapp.domain.model.BleDevice

/** Comprehensive BLE connection state with detailed intermediate states */
sealed interface BleConnectionState {
  /** Initial state - no active connection or scan */
  data object Idle : BleConnectionState

  /** Currently scanning for BLE devices */
  data object Scanning : BleConnectionState

  /** Scan failed with error */
  data class ScanFailed(val error: BleError) : BleConnectionState

  /** Attempting to connect to device */
  data class Connecting(val device: BleDevice) : BleConnectionState

  /** Connected, discovering GATT services */
  data class DiscoveringServices(val device: BleDevice) : BleConnectionState

  /** Fully connected with services discovered */
  data class Connected(val device: BleDevice, val services: List<BluetoothGattService>) :
    BleConnectionState

  /** Disconnecting from device */
  data class Disconnecting(val device: BleDevice) : BleConnectionState

  /** Disconnected (clean state) */
  data object Disconnected : BleConnectionState

  /** Connection or operation failed */
  data class Failed(val error: BleError) : BleConnectionState
}
