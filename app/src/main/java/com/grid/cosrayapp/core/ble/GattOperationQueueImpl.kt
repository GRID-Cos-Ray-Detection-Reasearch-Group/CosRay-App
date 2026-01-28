package com.grid.cosrayapp.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import com.grid.cosrayapp.core.common.CosRayResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementation of [GattOperationQueue] for serializing BLE GATT operations.
 *
 * Ensures only one GATT operation is in-flight at a time, as required
 * by the Android BLE stack.
 */
@Suppress("TooManyFunctions")
class GattOperationQueueImpl(
  private val scope: CoroutineScope,
  private val gattProvider: () -> BluetoothGatt?,
  private val serviceUuidProvider: () -> java.util.UUID?,
) : GattOperationQueue {

  private val operationChannel = Channel<GattOperation<*>>(Channel.UNLIMITED)
  private var _isProcessing = false
  override val isProcessing: Boolean get() = _isProcessing

  private var pendingWriteOperation: GattOperation.Write? = null

  /**
   * Start processing the operation queue.
   * Should be called after connection is established.
   */
  fun startProcessing() {
    if (_isProcessing) return
    scope.launch { processQueue() }
  }

  override suspend fun <T> enqueue(operation: GattOperation<T>): CosRayResult<T> {
    operationChannel.send(operation)
    return operation.deferred.await()
  }

  override fun clear() {
    // Drain the channel
    while (operationChannel.tryReceive().isSuccess) {
      // Discard operations
    }
    pendingWriteOperation = null
  }

  /**
   * Called from GATT callback when a characteristic write completes.
   */
  fun onCharacteristicWriteComplete(status: Int) {
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
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private suspend fun processQueue() {
    _isProcessing = true

    for (operation in operationChannel) {
      val gatt = gattProvider()
      if (gatt == null) {
        completeWithError(operation, "GATT not connected")
        continue
      }

      when (operation) {
        is GattOperation.Write -> handleWrite(gatt, operation)
        is GattOperation.Read -> handleRead(operation)
        is GattOperation.EnableNotifications -> handleNotifications(gatt, operation)
      }

      delay(OPERATION_DELAY_MS)
    }

    _isProcessing = false
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private fun handleWrite(gatt: BluetoothGatt, operation: GattOperation.Write) {
    pendingWriteOperation = operation

    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeCharacteristic(operation.characteristic, operation.data, operation.writeType) ==
        BluetoothGatt.GATT_SUCCESS
    } else {
      operation.characteristic.value = operation.data
      operation.characteristic.writeType = operation.writeType
      gatt.writeCharacteristic(operation.characteristic)
    }

    if (!success) {
      operation.deferred.complete(CosRayResult.Error(IllegalStateException("Write initiation failed")))
      pendingWriteOperation = null
    }
    // If success, wait for onCharacteristicWrite callback to complete the deferred
  }

  private fun handleRead(operation: GattOperation.Read) {
    // Read operation not yet fully implemented
    operation.deferred.complete(
      CosRayResult.Error(UnsupportedOperationException("Read not yet implemented"))
    )
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private fun handleNotifications(gatt: BluetoothGatt, operation: GattOperation.EnableNotifications) {
    val serviceUuid = serviceUuidProvider()
    if (serviceUuid == null) {
      operation.deferred.complete(CosRayResult.Error(IllegalStateException("No active service")))
      return
    }

    val service = gatt.getService(serviceUuid)
    if (service == null) {
      operation.deferred.complete(CosRayResult.Error(IllegalStateException("Service not found")))
      return
    }

    val characteristic = service.getCharacteristic(operation.characteristicUuid)
    if (characteristic == null) {
      operation.deferred.complete(CosRayResult.Error(IllegalStateException("Characteristic not found")))
      return
    }

    gatt.setCharacteristicNotification(characteristic, operation.enable)

    val descriptor = characteristic.getDescriptor(BleConfig.CLIENT_DESCRIPTOR_UUID)
    if (descriptor != null) {
      val value = if (operation.enable) {
        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      } else {
        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
      }

      val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
      } else {
        descriptor.setValue(value)
        gatt.writeDescriptor(descriptor)
      }

      if (success) {
        operation.deferred.complete(CosRayResult.Success(Unit))
      } else {
        operation.deferred.complete(CosRayResult.Error(IllegalStateException("Descriptor write failed")))
      }
    } else {
      // No descriptor, but notification was set
      operation.deferred.complete(CosRayResult.Success(Unit))
    }
  }

  private fun <T> completeWithError(operation: GattOperation<T>, message: String) {
    @Suppress("UNCHECKED_CAST")
    when (operation) {
      is GattOperation.Write -> operation.deferred.complete(CosRayResult.Error(IllegalStateException(message)))
      is GattOperation.Read -> operation.deferred.complete(CosRayResult.Error(IllegalStateException(message)))
      is GattOperation.EnableNotifications -> operation.deferred.complete(CosRayResult.Error(IllegalStateException(message)))
    }
  }

  companion object {
    private const val TAG = "GattOperationQueueImpl"
    private const val OPERATION_DELAY_MS = 100L
  }
}
