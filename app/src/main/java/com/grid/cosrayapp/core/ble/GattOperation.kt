package com.grid.cosrayapp.core.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.grid.cosrayapp.core.common.CosRayResult
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred

/**
 * Represents a GATT operation to be queued and executed sequentially.
 *
 * BLE GATT operations must be serialized - only one operation can be
 * in-flight at a time. This sealed class hierarchy represents all
 * supported operation types.
 */
sealed class GattOperation<T> {
  /** Deferred result that will be completed when the operation finishes. */
  abstract val deferred: CompletableDeferred<CosRayResult<T>>

  /**
   * Write data to a characteristic.
   *
   * @property characteristic The characteristic to write to.
   * @property data The data to write.
   * @property writeType The write type (default, no response, signed).
   * @property deferred Completion handler for the result.
   */
  data class Write(
    val characteristic: BluetoothGattCharacteristic,
    val data: ByteArray,
    val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
    override val deferred: CompletableDeferred<CosRayResult<Unit>>,
  ) : GattOperation<Unit>() {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as Write
      return characteristic.uuid == other.characteristic.uuid && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
      var result = characteristic.uuid.hashCode()
      result = 31 * result + data.contentHashCode()
      return result
    }
  }

  /**
   * Read data from a characteristic.
   *
   * @property characteristicUuid UUID of the characteristic to read.
   * @property deferred Completion handler for the result.
   */
  data class Read(
    val characteristicUuid: UUID,
    override val deferred: CompletableDeferred<CosRayResult<ByteArray>>,
  ) : GattOperation<ByteArray>()

  /**
   * Enable or disable notifications on a characteristic.
   *
   * @property characteristicUuid UUID of the characteristic.
   * @property enable True to enable, false to disable.
   * @property deferred Completion handler for the result.
   */
  data class EnableNotifications(
    val characteristicUuid: UUID,
    val enable: Boolean = true,
    override val deferred: CompletableDeferred<CosRayResult<Unit>>,
  ) : GattOperation<Unit>()
}

/**
 * Interface for managing GATT operation queue.
 *
 * Ensures BLE operations are executed sequentially as required by
 * the Android BLE stack.
 */
interface GattOperationQueue {
  /**
   * Enqueue a GATT operation for execution.
   *
   * The operation will be executed when all previously queued operations
   * have completed. Results are returned through the operation's deferred.
   *
   * @param operation The operation to enqueue.
   * @return Result of the operation.
   */
  suspend fun <T> enqueue(operation: GattOperation<T>): CosRayResult<T>

  /** Clear all pending operations from the queue. */
  fun clear()

  /** Check if queue processing is currently active. */
  val isProcessing: Boolean
}
