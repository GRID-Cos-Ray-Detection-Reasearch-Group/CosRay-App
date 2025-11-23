package com.travellerse.cosray_app.data.ble

import com.travellerse.cosray_app.core.ble.BleController
import com.travellerse.cosray_app.domain.model.BleDevice
import com.travellerse.cosray_app.domain.model.DeviceConnectionState
import com.travellerse.cosray_app.domain.model.TelemetrySample
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BleRepository(private val controller: BleController) {

    val scanResults: StateFlow<List<BleDevice>> = controller.scanResults
    val isScanning: StateFlow<Boolean> = controller.isScanning
    val connectionState: StateFlow<DeviceConnectionState> = controller.connectionState
    val telemetry: SharedFlow<TelemetrySample> = controller.telemetry

    fun startScan() = controller.startScan()
    fun stopScan() = controller.stopScan()
    fun connect(address: String) = controller.connect(address)
    fun disconnect() = controller.disconnect()
    fun hasPermissions(): Boolean = controller.hasBluetoothPermissions()
    fun sendCommand(command: ByteArray) = controller.sendCommand(command)
}
