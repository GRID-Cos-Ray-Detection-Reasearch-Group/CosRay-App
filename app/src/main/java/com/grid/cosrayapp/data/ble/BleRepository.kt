package com.grid.cosrayapp.data.ble

import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.core.ble.BleController
import com.grid.cosrayapp.core.ble.ScanConfig
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.TelemetrySample
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BleRepository(private val controller: BleController) {
  companion object {
    private const val SHARING_STARTED_TIMEOUT_MS = 5_000L
  }

  val scanResults: StateFlow<List<BleDevice>> = controller.scanResults
  val isScanning: StateFlow<Boolean> = controller.isScanning
  val connectionState: StateFlow<BleConnectionState> = controller.connectionState
  val telemetry: SharedFlow<TelemetrySample> = controller.telemetry

  /** Start BLE scan with optional configuration */
  suspend fun startScan(config: ScanConfig = ScanConfig.Default) =
    controller.startScanWithConfig(config)

  /** Stop ongoing BLE scan */
  fun stopScan() = controller.stopScan()

  /** Connect to device with timeout and retry */
  suspend fun connect(
    address: String,
    retries: Int = 3,
    timeoutMs: Long = 30_000L,
  ): CosRayResult<Unit> = controller.connectWithTimeout(address, retries, timeoutMs)

  /** Disconnect from current device */
  fun disconnect() = controller.disconnect()

  /** Check if required Bluetooth permissions are granted */
  fun hasPermissions(): Boolean = controller.hasBluetoothPermissions()

  /** Send command to connected device (queued) */
  suspend fun sendCommand(command: ByteArray): CosRayResult<Unit> =
    controller.sendCommandQueued(command)

  /** Send command to connected device (legacy, direct write) */
  fun sendCommandDirect(command: ByteArray): Boolean = controller.sendCommand(command)

  /** Check if currently connected to a device */
  val isConnected: StateFlow<Boolean> =
    connectionState
      .map { state -> state is BleConnectionState.Connected }
      .stateIn(
        scope = controller.externalScope,
        started = SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
        initialValue = false,
      )

  /** Get currently connected/connecting device */
  val currentDevice: StateFlow<BleDevice?> =
    connectionState
      .map { state ->
        when (state) {
          is BleConnectionState.Connected -> state.device
          is BleConnectionState.Connecting -> state.device
          is BleConnectionState.DiscoveringServices -> state.device
          is BleConnectionState.Disconnecting -> state.device
          else -> null
        }
      }
      .stateIn(
        scope = controller.externalScope,
        started = SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
        initialValue = null,
      )
}
