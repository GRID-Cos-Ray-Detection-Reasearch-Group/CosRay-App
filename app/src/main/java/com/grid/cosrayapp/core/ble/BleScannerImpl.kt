package com.grid.cosrayapp.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of [BleScanner] for Android BLE scanning.
 *
 * Handles device discovery with configurable filters, scan modes, and automatic scan duration
 * management.
 */
@Suppress("TooManyFunctions")
class BleScannerImpl(private val context: Context, private val scope: CoroutineScope) : BleScanner {

  private val bluetoothManager: BluetoothManager? =
    context.getSystemService(BluetoothManager::class.java)

  /** Resolve scanner on demand to handle Bluetooth being enabled after construction. */
  private val currentScanner
    get() = bluetoothManager?.adapter?.bluetoothLeScanner

  private val deviceCache = mutableMapOf<String, DiscoveredDevice>()

  private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
  override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  override val discoveredDevices: StateFlow<List<DiscoveredDevice>> =
    _discoveredDevices.asStateFlow()

  private var scanJob: Job? = null

  private val scanCallback =
    object : ScanCallback() {
      @SuppressLint("MissingPermission")
      override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        val scanResult = result ?: return
        val device = scanResult.device ?: return
        val now = Instant.now()

        val existing = deviceCache[device.address]
        val resolvedName = scanResult.scanRecord?.deviceName ?: device.name ?: existing?.name

        val advertisedServices =
          scanResult.scanRecord?.serviceUuids?.map { it.uuid }
            ?: existing?.advertisedServices
            ?: emptyList()

        val detectorId = existing?.detectorId ?: deriveDetectorId(device.address, resolvedName)

        val discoveredDevice =
          DiscoveredDevice(
            address = device.address,
            name = resolvedName,
            rssi = scanResult.rssi,
            detectorId = detectorId,
            scanRecord = scanResult.scanRecord?.bytes,
            advertisedServices = advertisedServices,
            lastSeen = now,
          )

        deviceCache[device.address] = discoveredDevice
        updateDeviceList()
      }

      override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        _scanState.value =
          ScanState.Failed(BleError.GattError(errorCode, "Scan failed with error code $errorCode"))
        Log.e(TAG, "Scan failed with error code: $errorCode")
      }
    }

  @SuppressLint("MissingPermission")
  override suspend fun startScan(config: ScanConfig): Result<Unit> =
    withContext(Dispatchers.Main) {
      if (_scanState.value is ScanState.Scanning) {
        Log.w(TAG, "Scan already in progress")
        return@withContext Result.success(Unit)
      }

      if (!hasBluetoothPermissions()) {
        val error = BleError.PermissionDenied(REQUIRED_PERMISSIONS)
        _scanState.value = ScanState.Failed(error)
        return@withContext Result.failure(SecurityException("Missing Bluetooth permissions"))
      }

      val adapter = bluetoothManager?.adapter
      if (adapter?.isEnabled != true) {
        val error = BleError.BluetoothDisabled()
        _scanState.value = ScanState.Failed(error)
        return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
      }

      val scanner = currentScanner
      if (scanner == null) {
        val error = BleError.BluetoothDisabled("Scanner not available")
        _scanState.value = ScanState.Failed(error)
        return@withContext Result.failure(IllegalStateException("BLE scanner not available"))
      }

      deviceCache.clear()
      _discoveredDevices.value = emptyList()
      _scanState.value = ScanState.Scanning

      val settings = ScanSettings.Builder().setScanMode(config.scanMode).build()
      val filters = buildScanFilters(config)

      scanner.startScan(filters, settings, scanCallback)

      // Auto-stop scan after duration
      scanJob?.cancel()
      scanJob =
        scope.launch {
          delay(config.scanDuration)
          if (_scanState.value is ScanState.Scanning) {
            stopScan()
          }
        }

      Result.success(Unit)
    }

  @SuppressLint("MissingPermission")
  override suspend fun stopScan(): Result<Unit> =
    withContext(Dispatchers.Main) {
      if (_scanState.value !is ScanState.Scanning) {
        return@withContext Result.success(Unit)
      }

      scanJob?.cancel()
      currentScanner?.stopScan(scanCallback)
      _scanState.value = ScanState.Idle

      Result.success(Unit)
    }

  override fun clearCache() {
    deviceCache.clear()
    _discoveredDevices.value = emptyList()
  }

  private fun updateDeviceList() {
    _discoveredDevices.update { deviceCache.values.sortedBy { it.name ?: it.address } }
  }

  private fun hasBluetoothPermissions(): Boolean =
    REQUIRED_PERMISSIONS.all { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

  private fun deriveDetectorId(address: String, name: String?): String =
    name?.takeIf { it.isNotBlank() }?.trim() ?: address.uppercase()

  private fun buildScanFilters(config: ScanConfig): List<ScanFilter> {
    val filters = mutableListOf<ScanFilter>()

    config.serviceUuids?.forEach { uuid ->
      filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build())
    }

    config.deviceNameFilter?.let { name ->
      filters.add(ScanFilter.Builder().setDeviceName(name).build())
    }

    return filters.ifEmpty { emptyList() }
  }

  companion object {
    private const val TAG = "BleScannerImpl"

    private val REQUIRED_PERMISSIONS = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(android.Manifest.permission.BLUETOOTH_SCAN)
        add(android.Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        add(android.Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }
  }
}
