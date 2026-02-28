package com.grid.cosrayapp.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.DetectorId
import com.grid.cosrayapp.domain.model.SignalStrength
import com.grid.cosrayapp.domain.model.TelemetrySample
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

@Suppress("TooManyFunctions", "ReturnCount", "MagicNumber")
class BleController(private val context: Context, val externalScope: CoroutineScope) {
  private val bluetoothManager: BluetoothManager? =
    context.getSystemService(BluetoothManager::class.java)
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
  private val bluetoothScanner = BluetoothLeScannerCompat.getScanner()

  private val deviceCache = mutableMapOf<String, BleDevice>()

  private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
  val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
  val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

  private val _telemetry = MutableSharedFlow<TelemetrySample>(extraBufferCapacity = 16)
  val telemetry = _telemetry.asSharedFlow()

  private val _rawPackets = MutableSharedFlow<RawPacket>(extraBufferCapacity = 32)
  val rawPackets = _rawPackets.asSharedFlow()

  private var activeDevice: BleDevice? = null
  private var activeServiceUuid: UUID? = null
  private var scanJob: Job? = null

  private val deviceManager =
    NordicBleDeviceManager(
      context = context,
      onPacketReceived = { value ->
        externalScope.launch {
          _rawPackets.emit(
            RawPacket(
              characteristicId = BleConfig.NOTIFY_CHARACTERISTIC_UUID,
              data = value.copyOf(),
            )
          )
        }
        val sample = BleTelemetryParser.parse(value, activeDevice)
        if (sample != null) {
          externalScope.launch { _telemetry.emit(sample) }
        }
      },
      onServiceResolved = { uuid -> activeServiceUuid = uuid },
    )

  private val scanCallback =
    object : ScanCallback() {
      @SuppressLint("MissingPermission")
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        val device = result.device ?: return
        val now = Instant.now()
        val existing = deviceCache[device.address]
        val signal = SignalStrength(result.rssi, now)
        val resolvedName = result.scanRecord?.deviceName ?: device.name ?: existing?.name
        val advertisedFromScan =
          result.scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val advertisedServices =
          advertisedFromScan.ifEmpty { existing?.advertisedServices ?: emptyList() }
        val detectorId = existing?.id ?: deriveDetectorId(device, result)
        val bleDevice =
          existing?.copy(
            name = resolvedName,
            signal = signal,
            lastSeen = now,
            advertisedServices = advertisedServices,
          )
            ?: BleDevice(
              id = detectorId,
              macAddress = device.address,
              name = resolvedName,
              signal = signal,
              lastSeen = now,
              advertisedServices = advertisedServices,
            )

        deviceCache[bleDevice.macAddress] = bleDevice
        _scanResults.update { devices ->
          devices
            .associateBy { it.macAddress }
            .toMutableMap()
            .apply { put(bleDevice.macAddress, bleDevice) }
            .values
            .sortedBy { it.name ?: it.macAddress }
        }
      }

      override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        _isScanning.value = false
        _connectionState.value =
          BleConnectionState.ScanFailed(
            BleError.GattError(errorCode, "Scan failed with error code $errorCode")
          )
        Log.e(TAG, "Scan failed with error code: $errorCode")
      }
    }

  init {
    deviceManager.setConnectionObserver(
      object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
          val resolved = activeDevice ?: deviceCache[device.address] ?: fallbackDevice(device)
          activeDevice = resolved
          _connectionState.value = BleConnectionState.Connecting(resolved)
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
          val resolved = activeDevice ?: deviceCache[device.address] ?: fallbackDevice(device)
          activeDevice = resolved
          _connectionState.value = BleConnectionState.DiscoveringServices(resolved)
        }

        override fun onDeviceReady(device: BluetoothDevice) {
          val resolved = activeDevice ?: deviceCache[device.address] ?: fallbackDevice(device)
          activeDevice = resolved
          _connectionState.value =
            BleConnectionState.Connected(device = resolved, services = emptyList())
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
          _connectionState.value =
            BleConnectionState.Failed(
              BleError.GattError(reason, "Failed to connect: ${getGattStatusMessage(reason)}")
            )
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
          val resolved = activeDevice ?: deviceCache[device.address] ?: fallbackDevice(device)
          _connectionState.value = BleConnectionState.Disconnecting(resolved)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
          activeDevice = null
          activeServiceUuid = null
          _connectionState.value = BleConnectionState.Disconnected
        }
      }
    )
  }

  fun hasBluetoothPermissions(): Boolean =
    REQUIRED_PERMISSIONS.all { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

  @SuppressLint("MissingPermission")
  suspend fun startScanWithConfig(config: ScanConfig = ScanConfig.Default) =
    withContext(Dispatchers.Main) {
      if (_isScanning.value) {
        Log.w(TAG, "Scan already in progress")
        return@withContext
      }

      if (!hasBluetoothPermissions()) {
        _connectionState.value =
          BleConnectionState.ScanFailed(BleError.PermissionDenied(REQUIRED_PERMISSIONS))
        return@withContext
      }

      if (bluetoothAdapter?.isEnabled != true) {
        _connectionState.value = BleConnectionState.ScanFailed(BleError.BluetoothDisabled())
        return@withContext
      }

      deviceCache.clear()
      _scanResults.value = emptyList()
      _connectionState.value = BleConnectionState.Scanning

      val settings = ScanSettings.Builder().setScanMode(config.scanMode).build()
      val filters = buildScanFilters(config)

      bluetoothScanner.startScan(filters, settings, scanCallback)
      _isScanning.value = true

      scanJob?.cancel()
      scanJob =
        externalScope.launch {
          delay(config.scanDuration)
          if (_isScanning.value) {
            stopScan()
          }
        }
    }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    if (!_isScanning.value) return
    scanJob?.cancel()
    bluetoothScanner.stopScan(scanCallback)
    _isScanning.value = false
    if (_connectionState.value is BleConnectionState.Scanning) {
      _connectionState.value = BleConnectionState.Idle
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun connectWithTimeout(
    address: String,
    retries: Int = 3,
    timeoutMs: Long = 30_000L,
  ): CosRayResult<Unit> {
    repeat(retries) { attempt ->
      val result = runCosRayCatching { performConnection(address, timeoutMs) }

      when (result) {
        is CosRayResult.Success -> return result
        is CosRayResult.Error -> {
          if (result.throwable is TimeoutCancellationException) {
            _connectionState.value = BleConnectionState.Failed(BleError.ConnectionTimeout(address))
          }

          if (attempt < retries - 1) {
            delay(1000L * (attempt + 1))
            Log.d(TAG, "Connection attempt ${attempt + 1} failed, retrying...")
          }
        }
      }
    }

    return CosRayResult.Error(IllegalStateException("Failed to connect after $retries attempts"))
  }

  @SuppressLint("MissingPermission")
  private suspend fun performConnection(address: String, timeoutMs: Long) =
    withContext(Dispatchers.Main) {
      val adapter = checkNotNull(bluetoothAdapter) { "Bluetooth adapter not available" }
      if (!hasBluetoothPermissions()) {
        throw SecurityException("Missing Bluetooth permissions")
      }

      val device = adapter.getRemoteDevice(address)
      val bleDevice = deviceCache[address] ?: fallbackDevice(device)
      activeDevice = bleDevice
      _connectionState.value = BleConnectionState.Connecting(bleDevice)
      stopScan()

      deviceManager.connectDevice(device, timeoutMs)

      if (_connectionState.value !is BleConnectionState.Connected) {
        error("Connection was not ready after connect request")
      }
    }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    _connectionState.value =
      activeDevice?.let { BleConnectionState.Disconnecting(it) } ?: BleConnectionState.Disconnected
    deviceManager.disconnectDevice()
  }

  suspend fun sendCommandQueued(command: ByteArray): CosRayResult<Unit> {
    if (!hasBluetoothPermissions()) {
      return CosRayResult.Error(SecurityException("Missing Bluetooth permissions"))
    }

    if (_connectionState.value !is BleConnectionState.Connected) {
      return CosRayResult.Error(IllegalStateException("Device not connected"))
    }

    if (activeServiceUuid == null) {
      return CosRayResult.Error(IllegalStateException("No active service"))
    }

    return runCosRayCatching {
      deviceManager.writeCommand(command)
      Unit
    }
  }

  fun shutdown() {
    stopScan()
    scanJob?.cancel()
    deviceManager.disconnectDevice()
    deviceManager.close()
    deviceCache.clear()
    activeDevice = null
    activeServiceUuid = null
    _scanResults.value = emptyList()
    _connectionState.value = BleConnectionState.Idle
  }

  @SuppressLint("MissingPermission")
  private fun fallbackDevice(device: BluetoothDevice): BleDevice {
    val now = Instant.now()
    return BleDevice(
      id = deriveDetectorId(device, null),
      macAddress = device.address,
      name = device.name,
      signal = SignalStrength(rssi = 0, updatedAt = now),
      lastSeen = now,
    )
  }

  private fun deriveDetectorId(device: BluetoothDevice, scanResult: ScanResult?): DetectorId {
    val advertisedId = scanResult?.scanRecord?.deviceName?.takeIf { it.isNotBlank() }?.trim()

    @SuppressLint("MissingPermission")
    val deviceName = device.name?.takeIf { it.isNotBlank() }?.trim()
    val identifier = advertisedId ?: deviceName ?: device.address.uppercase(Locale.US)
    return DetectorId(identifier)
  }

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

  private fun getGattStatusMessage(status: Int): String =
    when (status) {
      133 -> "GATT Error 133: Connection lost or device out of range"
      8 -> "GATT Error 8: Connection timeout"
      19 -> "GATT Error 19: Connection terminated by peer"
      22 -> "GATT Error 22: Link encryption failed"
      34 -> "GATT Error 34: Service changed"
      62 -> "GATT Error 62: Request not supported"
      else -> "GATT Error $status: Unknown error"
    }

  companion object {
    private const val TAG = "BleController"

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
