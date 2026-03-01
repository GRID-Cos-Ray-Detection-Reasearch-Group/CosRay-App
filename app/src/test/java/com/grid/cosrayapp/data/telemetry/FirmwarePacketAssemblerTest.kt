package com.grid.cosrayapp.data.telemetry

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwarePacketAssemblerTest {
  @Test
  fun `consume complete muon packet should return one request`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildMuonPacketBytes(pkgCnt = 1, utc = 1_710_000_000)

    val requests = assembler.consume(packet, "AA:BB:CC:DD:EE:FF")

    assertEquals(1, requests.size)
    assertEquals("muon", requests[0].packetType)
    assertEquals("AA:BB:CC:DD:EE:FF", requests[0].device)
  }

  @Test
  fun `consume split timeline packet should assemble across chunks`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildTimelinePacketBytes(pkgCnt = 7)

    val first = assembler.consume(packet.copyOfRange(0, 240), "11:22:33:44:55:66")
    val second = assembler.consume(packet.copyOfRange(240, packet.size), "11:22:33:44:55:66")

    assertEquals(0, first.size)
    assertEquals(1, second.size)
    assertEquals("timeline", second[0].packetType)
    assertEquals("11:22:33:44:55:66", second[0].device)
  }

  @Test
  fun `consume noise then packet should resync and parse`() {
    val assembler = FirmwarePacketAssembler()
    val noise = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
    val packet = buildMuonPacketBytes(pkgCnt = 9, utc = 1_710_000_999)
    val mixed = noise + packet

    val requests = assembler.consume(mixed, "AA:AA:AA:AA:AA:AA")

    assertEquals(1, requests.size)
    assertEquals("muon", requests[0].packetType)
  }

  private fun buildMuonPacketBytes(pkgCnt: Int, utc: Int): ByteArray {
    val data = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
    data.put(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()))
    data.putInt(pkgCnt)
    data.putInt(utc)
    repeat(35) { index ->
      data.putLong(1_000L + index)
      data.putShort((200 + index).toShort())
      data.putInt(300 + index)
    }
    data.put(byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()))
    data.put(ByteArray(6))

    val raw = data.array()
    val crc = calculateCrc16Ccitt(raw, startIndex = 3, length = 507)
    val footerBuffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    footerBuffer.position(510)
    footerBuffer.putShort(crc.toShort())
    return raw
  }

  private fun buildTimelinePacketBytes(pkgCnt: Int): ByteArray {
    val data = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
    data.put(byteArrayOf(0x12, 0x34, 0x56))
    data.putInt(pkgCnt)
    repeat(10) { index ->
      data.putLong(10_000L + index)
      data.putInt(100 + index)
      data.putInt(1_710_000_000 + index)
      data.putInt(90 + index)
      data.putLong(9_000L + index)
      data.putInt(120_000_000 + index)
      data.putInt(30_000_000 + index)
      data.putShort((123 + index).toShort())
      data.put(index.toByte())
      data.put((index + 1).toByte())
      data.put((index + 2).toByte())
      data.putShort((25 + index).toShort())
      data.put((30 + index).toByte())
      data.putShort((400 + index).toShort())
      data.putShort((500 + index).toShort())
    }
    data.put(byteArrayOf(0x78.toByte(), 0x9A.toByte(), 0xBC.toByte()))
    data.put(ByteArray(20))

    val raw = data.array()
    val crc = calculateCrc16Ccitt(raw, startIndex = 3, length = 507)
    val footerBuffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    footerBuffer.position(510)
    footerBuffer.putShort(crc.toShort())
    return raw
  }

  private fun calculateCrc16Ccitt(data: ByteArray, startIndex: Int, length: Int): Int {
    var crc = 0xFFFF
    for (index in startIndex until startIndex + length) {
      crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
      repeat(8) {
        crc =
          if ((crc and 0x8000) != 0) {
            ((crc shl 1) xor 0x1021) and 0xFFFF
          } else {
            (crc shl 1) and 0xFFFF
          }
      }
    }
    return crc and 0xFFFF
  }
}
