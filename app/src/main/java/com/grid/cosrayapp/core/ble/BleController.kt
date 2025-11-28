package com.grid.cosrayapp.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.DetectorId
import com.grid.cosrayapp.domain.model.SignalStrength
import com.grid.cosrayapp.domain.model.TelemetrySample
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BleController(private val context: Context, val externalScope: CoroutineScope) {
  private val bluetoothManager: BluetoothManager? =
    context.getSystemService(BluetoothManager::class.java)
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
  private val bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner

  private val deviceCache = mutableMapOf<String, BleDevice>()

  private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
  val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
  val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

  private val _telemetry = MutableSharedFlow<TelemetrySample>(extraBufferCapacity = 16)
  val telemetry = _telemetry.asSharedFlow()

  private var bluetoothGatt: BluetoothGatt? = null
  private var activeDevice: BleDevice? = null
  private var connectionJob: Job? = null
  private var scanJob: Job? = null

  // GATT operation queue
  private var pendingWriteOperation: GattOperation.Write? = null
  private val gattOperationQueue = Channel<GattOperation>(Channel.UNLIMITED)
  private var isProcessingQueue = false
  private var queueProcessingJob: Job? = null

  private val scanCallback =
    object : ScanCallback() {
      @SuppressLint("MissingPermission")
      override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        val scanResult = result ?: return
        val device = scanResult.device ?: return
        val now = Instant.now()
        val existing = deviceCache[device.address]
        val signal = SignalStrength(scanResult.rssi, now)
        val resolvedName = scanResult.scanRecord?.deviceName ?: device.name ?: existing?.name
        val advertisedFromScan =
          scanResult.scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val advertisedServices =
          advertisedFromScan.ifEmpty { existing?.advertisedServices ?: emptyList() }
        val detectorId = existing?.id ?: deriveDetectorId(device, scanResult)
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

  private val gattCallback =
    object : BluetoothGattCallback() {
      @SuppressLint("MissingPermission")
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        when {
          status != BluetoothGatt.GATT_SUCCESS -> {
            handleGattError(status, "Connection state change failed")
            closeConnection(
              BleConnectionState.Failed(BleError.GattError(status, getGattStatusMessage(status)))
            )
          }

          newState == BluetoothProfile.STATE_CONNECTED -> {
            val device = activeDevice ?: fallbackDevice(gatt.device)
            _connectionState.value = BleConnectionState.DiscoveringServices(device)
            gatt.discoverServices()
          }

          newState == BluetoothProfile.STATE_DISCONNECTED -> {
            closeConnection(BleConnectionState.Disconnected)
          }
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        when {
          status != BluetoothGatt.GATT_SUCCESS -> {
            closeConnection(
              BleConnectionState.Failed(BleError.GattError(status, "Service discovery failed"))
            )
          }

          else -> {
            val service = gatt.getService(BleConfig.SERVICE_UUID)
            val notifyCharacteristic =
              service?.getCharacteristic(BleConfig.NOTIFY_CHARACTERISTIC_UUID)

            if (notifyCharacteristic == null) {
              closeConnection(
                BleConnectionState.Failed(BleError.ServiceNotFound(BleConfig.SERVICE_UUID))
              )
              return
            }

            enableNotifications(gatt, notifyCharacteristic)
            val device = activeDevice ?: fallbackDevice(gatt.device)
            activeDevice = device
            _connectionState.value =
              BleConnectionState.Connected(device = device, services = gatt.services)

            // Start GATT operation queue processing
            startQueueProcessing()
          }
        }
      }

      override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
      ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        val sample = BleTelemetryParser.parse(value, activeDevice)
        if (sample != null) {
          externalScope.launch { _telemetry.emit(sample) }
        }
      }

      override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
      ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        // Resolve the pending write operation's deferred based on status
        pendingWriteOperation?.let { operation ->
          if (status == BluetoothGatt.GATT_SUCCESS) {
            operation.deferred.complete(CosRayResult.Success(Unit))
          } else {
            operation.deferred.complete(
              CosRayResult.Error(IllegalStateException("Write failed with status $status"))
            )
          }
          pendingWriteOperation = null
        }
        Log.d(TAG, "Characteristic write completed with status: $status")
      }

      override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
      ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        if (status != BluetoothGatt.GATT_SUCCESS) {
          Log.e(TAG, "Descriptor write failed with status: $status")
        }
      }
    }

  fun hasBluetoothPermissions(): Boolean =
    REQUIRED_PERMISSIONS.all { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

  /** Start BLE scan with configuration */
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

      val scanner = bluetoothScanner
      if (scanner == null) {
        _connectionState.value =
          BleConnectionState.ScanFailed(BleError.BluetoothDisabled("Scanner not available"))
        return@withContext
      }

      deviceCache.clear()
      _scanResults.value = emptyList()
      _connectionState.value = BleConnectionState.Scanning

      val settings = ScanSettings.Builder().setScanMode(config.scanMode).build()

      val filters = buildScanFilters(config)

      scanner.startScan(filters, settings, scanCallback)
      _isScanning.value = true

      // Auto-stop scan after duration
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
    bluetoothScanner?.stopScan(scanCallback)
    _isScanning.value = false
    if (_connectionState.value is BleConnectionState.Scanning) {
      _connectionState.value = BleConnectionState.Idle
    }
  }

  /** Connect to device with timeout and retry support */
  @SuppressLint("MissingPermission")
  suspend fun connectWithTimeout(
    address: String,
    retries: Int = 3,
    timeoutMs: Long = 30_000L,
  ): CosRayResult<Unit> {
    repeat(retries) { attempt ->
      val result = runCosRayCatching { withTimeout(timeoutMs) { performConnection(address) } }

      when (result) {
        is CosRayResult.Success -> {
          return result
        }

        is CosRayResult.Error -> {
          if (result.throwable is TimeoutCancellationException) {
            _connectionState.value = BleConnectionState.Failed(BleError.ConnectionTimeout(address))
          }

          if (attempt < retries - 1) {
            delay(1000L * (attempt + 1)) // Exponential backoff
            Log.d(TAG, "Connection attempt ${attempt + 1} failed, retrying...")
          }
        }
      }
    }

    return CosRayResult.Error(IllegalStateException("Failed to connect after $retries attempts"))
  }

  @SuppressLint("MissingPermission")
  private suspend fun performConnection(address: String) =
    withContext(Dispatchers.Main) {
      val adapter =
        bluetoothAdapter ?: throw IllegalStateException("Bluetooth adapter not available")
      if (!hasBluetoothPermissions()) {
        throw SecurityException("Missing Bluetooth permissions")
      }

      val device = adapter.getRemoteDevice(address)
      val bleDevice = deviceCache[address] ?: fallbackDevice(device)
      activeDevice = bleDevice
      _connectionState.value = BleConnectionState.Connecting(bleDevice)
      stopScan()
      bluetoothGatt?.close()

      bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

      // Wait for connection
      var attempts = 0
      while (attempts < 60 && _connectionState.value !is BleConnectionState.Connected) {
        delay(500)
        attempts++
        if (_connectionState.value is BleConnectionState.Failed) {
          throw IllegalStateException("Connection failed")
        }
      }

      if (_connectionState.value !is BleConnectionState.Connected) {
        throw IllegalStateException("Connection timeout")
      }
    }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    _connectionState.value =
      activeDevice?.let { BleConnectionState.Disconnecting(it) } ?: BleConnectionState.Disconnected

    bluetoothGatt?.disconnect()
    closeConnection(BleConnectionState.Disconnected)
  }

  /** Send command using GATT operation queue */
  suspend fun sendCommandQueued(command: ByteArray): CosRayResult<Unit> {
    val gatt = bluetoothGatt ?: return CosRayResult.Error(IllegalStateException("Not connected"))

    if (!hasBluetoothPermissions()) {
      return CosRayResult.Error(SecurityException("Missing Bluetooth permissions"))
    }

    if (_connectionState.value !is BleConnectionState.Connected) {
      return CosRayResult.Error(IllegalStateException("Device not connected"))
    }

    val service =
      gatt.getService(BleConfig.SERVICE_UUID)
        ?: return CosRayResult.Error(IllegalStateException("Service not found"))

    val writeCharacteristic =
      service.getCharacteristic(BleConfig.WRITE_CHARACTERISTIC_UUID)
        ?: return CosRayResult.Error(IllegalStateException("Write characteristic not found"))

    val deferred = CompletableDeferred<CosRayResult<Unit>>()
    val operation =
      GattOperation.Write(
        characteristic = writeCharacteristic,
        data = command,
        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        deferred = deferred,
      )

    gattOperationQueue.send(operation)
    return deferred.await()
  }

  /** Legacy sendCommand for backward compatibility */
  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  fun sendCommand(command: ByteArray): Boolean {
    val gatt = bluetoothGatt ?: return false
    if (!hasBluetoothPermissions()) return false
    if (_connectionState.value !is BleConnectionState.Connected) return false

    val service = gatt.getService(BleConfig.SERVICE_UUID) ?: return false
    val writeCharacteristic =
      service.getCharacteristic(BleConfig.WRITE_CHARACTERISTIC_UUID) ?: return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val result =
        gatt.writeCharacteristic(
          writeCharacteristic,
          command,
          BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
      result == BluetoothGatt.GATT_SUCCESS
    } else {
      writeCharacteristic.value = command
      gatt.writeCharacteristic(writeCharacteristic)
    }
  }

  private fun startQueueProcessing() {
    if (isProcessingQueue) return

    queueProcessingJob?.cancel()
    queueProcessingJob = externalScope.launch { processGattQueue() }
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private suspend fun processGattQueue() {
    isProcessingQueue = true

    for (operation in gattOperationQueue) {
      val gatt = bluetoothGatt
      if (gatt == null) {
        when (operation) {
          is GattOperation.Write -> {
            operation.deferred.complete(
              CosRayResult.Error(IllegalStateException("GATT not connected"))
            )
          }

          is GattOperation.Read -> {
            operation.deferred.complete(
              CosRayResult.Error(IllegalStateException("GATT not connected"))
            )
          }

          is GattOperation.EnableNotifications -> {
            operation.deferred.complete(
              CosRayResult.Error(IllegalStateException("GATT not connected"))
            )
          }
        }
        continue
      }

      when (operation) {
        is GattOperation.Write -> {
          // Store the operation to be completed in onCharacteristicWrite callback
          pendingWriteOperation = operation
          // Initiate the write
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(operation.characteristic, operation.data, operation.writeType)
          } else {
            operation.characteristic.value = operation.data
            operation.characteristic.writeType = operation.writeType
            gatt.writeCharacteristic(operation.characteristic)
          }
          // Do not complete the deferred here; it will be resolved in onCharacteristicWrite
        }

        is GattOperation.Read -> {
          // TODO: Implement read operation
          operation.deferred.complete(
            CosRayResult.Error(UnsupportedOperationException("Read not yet implemented"))
          )
        }

        is GattOperation.EnableNotifications -> {
          // TODO: Implement notification toggle
          operation.deferred.complete(
            CosRayResult.Error(
              UnsupportedOperationException("Notification toggle not yet implemented")
            )
          )
        }
      }

      delay(100) // Small delay between operations
    }

    isProcessingQueue = false
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private fun enableNotifications(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
  ) {
    gatt.setCharacteristicNotification(characteristic, true)
    val descriptor = characteristic.getDescriptor(BleConfig.CLIENT_DESCRIPTOR_UUID)
    descriptor?.let {
      val notificationValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(it, notificationValue)
      } else {
        it.setValue(notificationValue)
        gatt.writeDescriptor(it)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun closeConnection(state: BleConnectionState) {
    bluetoothGatt?.close()
    bluetoothGatt = null
    activeDevice = null
    queueProcessingJob?.cancel()
    isProcessingQueue = false
    _connectionState.value = state
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

  private fun handleGattError(status: Int, message: String) {
    val errorMessage = getGattStatusMessage(status)
    Log.e(TAG, "$message: $errorMessage")
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

  fun shutdown() {
    stopScan()
    connectionJob?.cancel()
    scanJob?.cancel()
    queueProcessingJob?.cancel()
    disconnect()
    deviceCache.clear()
    _scanResults.value = emptyList()
    _connectionState.value = BleConnectionState.Idle
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
