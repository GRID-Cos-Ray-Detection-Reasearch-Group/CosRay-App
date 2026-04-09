package com.grid.cosrayapp.core.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RawPacketTest {
  @Test
  fun `raw packet equality should ignore id and timestamp`() {
    val characteristicId = UUID.fromString("09070605-0403-0201-efcd-ab8967452301")
    val left =
      RawPacket(
        id = 1L,
        characteristicId = characteristicId,
        data = byteArrayOf(0x01, 0x02, 0x03),
        timestamp = 1_700_000_000_000L,
      )
    val right =
      RawPacket(
        id = 2L,
        characteristicId = characteristicId,
        data = byteArrayOf(0x01, 0x02, 0x03),
        timestamp = 1_700_000_000_123L,
      )

    assertEquals(left, right)
    assertEquals(left.hashCode(), right.hashCode())
    assertNotEquals(left.id, right.id)
  }
}
