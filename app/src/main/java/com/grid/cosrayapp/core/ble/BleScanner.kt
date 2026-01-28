package com.grid.cosrayapp.core.ble

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for BLE device scanning operations.
 *
 * Implementations handle device discovery, scan lifecycle management,
 * and expose discovered devices through reactive flows.
 */
interface BleScanner {
  /** Current state of the scanning operation. */
  val scanState: StateFlow<ScanState>

  /** List of devices discovered during scanning. */
  val discoveredDevices: StateFlow<List<DiscoveredDevice>>

  /**
   * Start scanning for BLE devices with the given configuration.
   *
   * @param config Scan configuration including filters, mode, and duration.
   * @return Result indicating success or failure with error details.
   */
  suspend fun startScan(config: ScanConfig = ScanConfig.Default): Result<Unit>

  /**
   * Stop any ongoing scan.
   *
   * @return Result indicating success or failure.
   */
  suspend fun stopScan(): Result<Unit>

  /** Clear cached discovered devices. */
  fun clearCache()
}
