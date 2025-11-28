package com.travellerse.cosray_app.core.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.travellerse.cosray_app.core.common.CosRayResult
import kotlinx.coroutines.CompletableDeferred

/** GATT operations that must be queued to prevent concurrent access */
sealed class GattOperation {
  /** Write data to a characteristic */
  data class Write(
    val characteristic: BluetoothGattCharacteristic,
    val data: ByteArray,
    val writeType: Int,
    val deferred: CompletableDeferred<CosRayResult<Unit>>,
  ) : GattOperation() {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as Write
      if (characteristic != other.characteristic) return false
      if (!data.contentEquals(other.data)) return false
      if (writeType != other.writeType) return false
      return true
    }

    override fun hashCode(): Int {
      var result = characteristic.hashCode()
      result = 31 * result + data.contentHashCode()
      result = 31 * result + writeType
      return result
    }
  }

  /** Read data from a characteristic */
  data class Read(
    val characteristic: BluetoothGattCharacteristic,
    val deferred: CompletableDeferred<CosRayResult<ByteArray>>,
  ) : GattOperation()

  /** Enable or disable notifications for a characteristic */
  data class EnableNotifications(
    val characteristic: BluetoothGattCharacteristic,
    val enable: Boolean,
    val deferred: CompletableDeferred<CosRayResult<Unit>>,
  ) : GattOperation()
}
