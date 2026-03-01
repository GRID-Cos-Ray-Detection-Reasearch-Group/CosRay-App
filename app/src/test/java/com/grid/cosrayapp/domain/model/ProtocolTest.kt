package com.grid.cosrayapp.domain.model

import com.grid.cosrayapp.domain.mapper.ProtocolMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolTest {
  @Test
  fun `muon packet parse with crc16 should succeed`() {
    val raw = buildMuonPacketBytes(pkgCnt = 42, utc = 1_710_000_000)

    val packet = Protocol.MuonDataPkg.fromRawData(raw)

    assertEquals(42, packet.pkgCnt)
    assertEquals(1_710_000_000, packet.utc)
    assertEquals(35, packet.muonDataList.size)
    assertEquals(0xAA.toByte(), packet.head[0])
    assertEquals(0xFF.toByte(), packet.tail[2])
  }

  @Test
  fun `timeline packet parse with crc16 should succeed`() {
    val raw = buildTimelinePacketBytes(pkgCnt = 7)

    val packet = Protocol.TimeLinePkg.fromRawData(raw)

    assertEquals(7, packet.pkgCnt)
    assertEquals(10, packet.timeLineDataList.size)
    assertEquals(0x12.toByte(), packet.head[0])
    assertEquals(0xBC.toByte(), packet.tail[2])
  }

  @Test
  fun `protocol mapper should create upload request from muon packet`() {
    val raw = buildMuonPacketBytes(pkgCnt = 11, utc = 1_710_000_100)
    val packet = Protocol.MuonDataPkg.fromRawData(raw)

    val request = ProtocolMapper.createMuonPacketRequest("AA:BB:CC:DD:EE:FF", packet)

    assertEquals("AA:BB:CC:DD:EE:FF", request.device)
    assertEquals("muon", request.packetType)
    assertNotNull(request.muonPacket)
    assertEquals(35, request.muonPacket?.events?.size)
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
