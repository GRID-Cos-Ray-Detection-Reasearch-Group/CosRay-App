package com.travellerse.cosray_app.core.ble

import android.bluetooth.le.ScanSettings
import java.util.UUID

/** Configuration for BLE scanning */
data class ScanConfig(
  /** Scan mode - LOW_LATENCY, BALANCED, or LOW_POWER */
  val scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
  /** How long to scan before automatically stopping (milliseconds) */
  val scanDuration: Long = 10_000L,
  /** Filter by service UUIDs (null = no filter) */
  val serviceUuids: List<UUID>? = null,
  /** Filter by device name (null = no filter) */
  val deviceNameFilter: String? = null,
) {
  companion object {
    /** Default configuration for general scanning */
    val Default = ScanConfig()

    /** Low power scan for battery conservation */
    val LowPower = ScanConfig(scanMode = ScanSettings.SCAN_MODE_LOW_POWER, scanDuration = 15_000L)

    /** Balanced scan mode */
    val Balanced = ScanConfig(scanMode = ScanSettings.SCAN_MODE_BALANCED, scanDuration = 12_000L)

    /** Fast scan for quick device discovery */
    val Fast = ScanConfig(scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY, scanDuration = 5_000L)
  }
}
