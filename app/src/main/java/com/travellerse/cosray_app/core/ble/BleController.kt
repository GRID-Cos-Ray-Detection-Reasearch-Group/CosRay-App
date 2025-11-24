package com.travellerse.cosray_app.core.ble

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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.travellerse.cosray_app.R
import com.travellerse.cosray_app.domain.model.BleDevice
import com.travellerse.cosray_app.domain.model.DetectorId
import com.travellerse.cosray_app.domain.model.DeviceConnectionState
import com.travellerse.cosray_app.domain.model.SignalStrength
import com.travellerse.cosray_app.domain.model.TelemetrySample
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BleController(private val context: Context, private val externalScope: CoroutineScope) {

    private val bluetoothManager: BluetoothManager? =
            context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner

    private val deviceCache = mutableMapOf<String, BleDevice>()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState =
            MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableSharedFlow<TelemetrySample>(extraBufferCapacity = 16)
    val telemetry = _telemetry.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeDevice: BleDevice? = null

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
                    val resolvedName =
                            scanResult.scanRecord?.deviceName ?: device.name ?: existing?.name
                    val advertisedFromScan =
                            scanResult
                                    .scanRecord
                                    ?.serviceUuids
                                    ?.map { it.uuid.toString() }
                                    .orEmpty()
                    val advertisedServices =
                            advertisedFromScan.ifEmpty {
                                existing?.advertisedServices ?: emptyList()
                            }
                    val detectorId = existing?.id ?: deriveDetectorId(device, scanResult)
                    val bleDevice =
                            existing?.copy(
                                    name = resolvedName,
                                    signal = signal,
                                    lastSeen = now,
                                    advertisedServices = advertisedServices
                            )
                                    ?: BleDevice(
                                            id = detectorId,
                                            macAddress = device.address,
                                            name = resolvedName,
                                            signal = signal,
                                            lastSeen = now,
                                            advertisedServices = advertisedServices
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
                }
            }

    private val gattCallback =
            object : BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                ) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        closeConnection(
                                DeviceConnectionState.Failed(
                                        context.getString(R.string.device_error_gatt, status)
                                )
                        )
                        return
                    }
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            _connectionState.value =
                                    DeviceConnectionState.Connecting(
                                            activeDevice ?: fallbackDevice(gatt.device)
                                    )
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            closeConnection(DeviceConnectionState.Disconnected)
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        closeConnection(
                                DeviceConnectionState.Failed(
                                        context.getString(R.string.device_error_service_discovery)
                                )
                        )
                        return
                    }
                    val service = gatt.getService(BleConfig.SERVICE_UUID)
                    val notifyCharacteristic =
                            service?.getCharacteristic(BleConfig.NOTIFY_CHARACTERISTIC_UUID)
                    if (notifyCharacteristic == null) {
                        closeConnection(
                                DeviceConnectionState.Failed(
                                        context.getString(R.string.device_error_notify_missing)
                                )
                        )
                        return
                    }
                    enableNotifications(gatt, notifyCharacteristic)
                    val device = activeDevice ?: fallbackDevice(gatt.device)
                    activeDevice = device
                    _connectionState.value = DeviceConnectionState.Connected(device)
                }

                override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                ) {
                    super.onCharacteristicChanged(gatt, characteristic, value)
                    val sample = BleTelemetryParser.parse(value, activeDevice)
                    if (sample != null) {
                        externalScope.launch { _telemetry.emit(sample) }
                    }
                }

                override fun onDescriptorWrite(
                        gatt: BluetoothGatt,
                        descriptor: BluetoothGattDescriptor,
                        status: Int
                ) {
                    super.onDescriptorWrite(gatt, descriptor, status)
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        closeConnection(
                                DeviceConnectionState.Failed(
                                        context.getString(R.string.device_error_descriptor_write)
                                )
                        )
                    }
                }
            }

    fun hasBluetoothPermissions(): Boolean =
            REQUIRED_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
            }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        if (!hasBluetoothPermissions()) return
        if (bluetoothAdapter?.isEnabled != true) return
        bluetoothScanner ?: return

        deviceCache.clear()
        _scanResults.value = emptyList()

        val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bluetoothScanner.startScan(null, settings, scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        bluetoothScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions()) return
        val device = adapter.getRemoteDevice(address)
        val bleDevice = deviceCache[address] ?: fallbackDevice(device)
        activeDevice = bleDevice
        _connectionState.value = DeviceConnectionState.Connecting(bleDevice)
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        closeConnection(DeviceConnectionState.Disconnected)
    }

    /**
     * Send a command to the connected BLE device
     * @param command The command bytes to send
     * @return true if the write operation was initiated successfully, false otherwise
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun sendCommand(command: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        if (!hasBluetoothPermissions()) return false
        if (connectionState.value !is DeviceConnectionState.Connected) return false

        val service = gatt.getService(BleConfig.SERVICE_UUID) ?: return false
        val writeCharacteristic = service.getCharacteristic(BleConfig.WRITE_CHARACTERISTIC_UUID) ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                writeCharacteristic,
                command,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            result == BluetoothGatt.GATT_SUCCESS
        } else {
            writeCharacteristic.value = command
            gatt.writeCharacteristic(writeCharacteristic)
        }
    }

    /**
     * Shutdown the BLE controller and release all resources
     * Should be called when the controller is no longer needed
     */
    @SuppressLint("MissingPermission")
    fun shutdown() {
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        activeDevice = null
        deviceCache.clear()
        _scanResults.value = emptyList()
        _connectionState.value = DeviceConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleConfig.CLIENT_DESCRIPTOR_UUID)
        descriptor?.let { descriptor ->
            val notificationValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, notificationValue)
            } else {
                descriptor.setValue(notificationValue)
                @Suppress("DEPRECATION") gatt.writeDescriptor(descriptor)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeConnection(state: DeviceConnectionState) {
        bluetoothGatt?.close()
        bluetoothGatt = null
        activeDevice = null
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
                lastSeen = now
        )
    }

    private fun deriveDetectorId(device: BluetoothDevice, scanResult: ScanResult?): DetectorId {
        val advertisedId = scanResult?.scanRecord?.deviceName?.takeIf { it.isNotBlank() }?.trim()
        @SuppressLint("MissingPermission")
        val deviceName = device.name?.takeIf { it.isNotBlank() }?.trim()
        val identifier = advertisedId ?: deviceName ?: device.address.uppercase(Locale.US)
        return DetectorId(identifier)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = buildList {
            add(android.Manifest.permission.BLUETOOTH_SCAN)
            add(android.Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}
