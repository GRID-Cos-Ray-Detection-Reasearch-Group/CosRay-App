package com.grid.cosrayapp.core.ble

import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.flow.StateFlow

/**
 * Configuration for BLE connection behavior.
 *
 * @property retries Number of connection attempts before failing.
 * @property timeoutMs Maximum time to wait for connection in milliseconds.
 * @property autoConnect Whether to use autoConnect mode (slower but more reliable for bonded
 *   devices).
 * @property requestHighPriority Whether to request high priority connection.
 * @property preferredMtu Preferred MTU size to negotiate.
 */
data class ConnectionConfig(
  val retries: Int = 3,
  val timeoutMs: Long = 30_000L,
  val autoConnect: Boolean = false,
  val requestHighPriority: Boolean = true,
  val preferredMtu: Int = 247,
) {
  companion object {
    val Default = ConnectionConfig()
  }
}

/**
 * Interface for BLE device connection operations.
 *
 * Implementations handle connection establishment, MTU negotiation, and connection lifecycle
 * management.
 */
interface BleConnector {
  /** Current connection state. */
  val connectionState: StateFlow<BleConnectionState>

  /** Current negotiated MTU size. */
  val mtu: StateFlow<Int>

  /**
   * Connect to a BLE device by MAC address.
   *
   * @param address MAC address of the device to connect to.
   * @param config Connection configuration options.
   * @return Result containing the BluetoothGatt instance on success, or error details on failure.
   */
  suspend fun connect(
    address: String,
    config: ConnectionConfig = ConnectionConfig.Default,
  ): Result<BluetoothGatt>

  /**
   * Disconnect from the currently connected device.
   *
   * @return Result indicating success or failure.
   */
  suspend fun disconnect(): Result<Unit>

  /**
   * Request a specific MTU size.
   *
   * @param mtu Desired MTU size.
   * @return Result containing the negotiated MTU on success.
   */
  suspend fun requestMtu(mtu: Int): Result<Int>
}
