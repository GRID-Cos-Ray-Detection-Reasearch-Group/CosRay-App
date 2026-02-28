package com.grid.cosrayapp.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import java.util.UUID
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend

internal class NordicBleDeviceManager(
  context: Context,
  private val onPacketReceived: (ByteArray) -> Unit,
  private val onServiceResolved: (UUID) -> Unit,
) : BleManager(context) {
  private var notifyCharacteristic: BluetoothGattCharacteristic? = null
  private var writeCharacteristic: BluetoothGattCharacteristic? = null
  private var serviceUuid: UUID? = null

  override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    val service = BleConfig.SUPPORTED_SERVICE_UUIDS.firstNotNullOfOrNull { gatt.getService(it) }
    if (service == null) {
      notifyCharacteristic = null
      writeCharacteristic = null
      serviceUuid = null
      return false
    }

    serviceUuid = service.uuid
    notifyCharacteristic = service.getCharacteristic(BleConfig.NOTIFY_CHARACTERISTIC_UUID)
    writeCharacteristic = service.getCharacteristic(BleConfig.WRITE_CHARACTERISTIC_UUID)
    return notifyCharacteristic != null && writeCharacteristic != null
  }

  override fun initialize() {
    requestMtu(PREFERRED_MTU).enqueue()
    setNotificationCallback(notifyCharacteristic).with { _, data ->
      data.value?.let { onPacketReceived(it) }
    }
    enableNotifications(notifyCharacteristic).enqueue()
    serviceUuid?.let(onServiceResolved)
  }

  override fun onServicesInvalidated() {
    notifyCharacteristic = null
    writeCharacteristic = null
    serviceUuid = null
  }

  suspend fun connectDevice(device: BluetoothDevice, timeoutMs: Long) {
    connect(device).retry(0).timeout(timeoutMs).suspend()
  }

  fun disconnectDevice() {
    disconnect().enqueue()
  }

  suspend fun writeCommand(command: ByteArray) {
    writeCharacteristic(writeCharacteristic, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
      .suspend()
  }

  companion object {
    private const val PREFERRED_MTU = 247
  }
}
