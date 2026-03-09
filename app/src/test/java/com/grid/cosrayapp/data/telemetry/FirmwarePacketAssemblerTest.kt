package com.grid.cosrayapp.data.telemetry

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwarePacketAssemblerTest {
  @Test
  fun `consume complete muon ble fragments should return one request`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildMuonPacketBytes(pkgCnt = 1, utc = 1_710_000_000)
    val chunks = buildBleFragments(packet, globalTotal = 1, globalIndex = 1)

    val requests = chunks.flatMap { assembler.consume(it, "AA:BB:CC:DD:EE:FF") }

    assertEquals(1, requests.size)
    assertEquals("muon", requests[0].uploadRequest.packetType)
    assertEquals("AA:BB:CC:DD:EE:FF", requests[0].uploadRequest.device)
    assertEquals(35, requests[0].uploadRequest.muonPacket?.events?.size)
    assertEquals(35, requests[0].samples.size)
  }

  @Test
  fun `consume split timeline fragments should assemble across chunks`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildTimelinePacketBytes(pkgCnt = 7)
    val chunks = buildBleFragments(packet, globalTotal = 2, globalIndex = 1)

    val first = chunks.take(10).flatMap { assembler.consume(it, "11:22:33:44:55:66") }
    val second = chunks.drop(10).flatMap { assembler.consume(it, "11:22:33:44:55:66") }

    assertEquals(0, first.size)
    assertEquals(1, second.size)
    assertEquals("timeline", second[0].uploadRequest.packetType)
    assertEquals("11:22:33:44:55:66", second[0].uploadRequest.device)
    assertEquals(10, second[0].uploadRequest.timelinePacket?.events?.size)
  }

  @Test
  fun `consume invalid fragment then packet should still parse`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildMuonPacketBytes(pkgCnt = 9, utc = 1_710_000_999)
    val invalid = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x05)
    val chunks = buildBleFragments(packet, globalTotal = 1, globalIndex = 1)

    val requests = mutableListOf<ParsedFirmwarePacket>()
    requests += assembler.consume(invalid, "AA:AA:AA:AA:AA:AA")
    chunks.forEach { requests += assembler.consume(it, "AA:AA:AA:AA:AA:AA") }

    assertEquals(1, requests.size)
    assertEquals("muon", requests[0].uploadRequest.packetType)
  }

  @Test
  fun `consume muon packet should drop zero-filled placeholder events`() {
    val assembler = FirmwarePacketAssembler()
    val packet = buildMuonPacketBytes(pkgCnt = 5, utc = 1_710_000_321, validEventCount = 2)
    val chunks = buildBleFragments(packet, globalTotal = 1, globalIndex = 1)

    val requests = chunks.flatMap { assembler.consume(it, "12:34:56:78:9A:BC") }

    assertEquals(1, requests.size)
    assertEquals(2, requests[0].uploadRequest.muonPacket?.events?.size)
    assertEquals(2, requests[0].samples.size)
  }

  private fun buildBleFragments(
    packet: ByteArray,
    globalTotal: Int,
    globalIndex: Int,
  ): List<ByteArray> {
    val payloadSize = 18
    val localTotal = (packet.size + payloadSize - 1) / payloadSize
    return (0 until localTotal).map { localIndex ->
      val fragment = ByteArray(22)
      fragment[0] = globalTotal.toByte()
      fragment[1] = globalIndex.toByte()
      fragment[2] = localTotal.toByte()
      fragment[3] = (localIndex + 1).toByte()
      val start = localIndex * payloadSize
      val end = minOf(start + payloadSize, packet.size)
      packet.copyInto(
        destination = fragment,
        destinationOffset = 4,
        startIndex = start,
        endIndex = end,
      )
      fragment
    }
  }

  private fun buildMuonPacketBytes(pkgCnt: Int, utc: Int, validEventCount: Int = 35): ByteArray {
    val totalSize = 3 + 4 + 4 + (35 * 14) + 3 + 6 + 2 // 512
    val data = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    data.put(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()))
    data.putInt(pkgCnt)
    data.putInt(utc)
    repeat(35) { index ->
      if (index < validEventCount) {
        data.putLong(1_000L + index)
        data.putShort((200 + index).toShort())
        data.putInt(300 + index)
      } else {
        data.putLong(0)
        data.putShort(0)
        data.putInt(0)
      }
    }
    data.put(byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()))
    data.put(ByteArray(6))

    val raw = data.array()
    val crc = calculateCrc16Ccitt(raw, startIndex = 0, length = totalSize - 2)
    val footerBuffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    footerBuffer.position(totalSize - 2)
    footerBuffer.putShort(crc.toShort())
    return raw
  }

  private fun buildTimelinePacketBytes(pkgCnt: Int): ByteArray {
    val totalSize = 3 + 4 + (10 * 48) + 3 + 20 + 2 // 512
    val data = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
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
    val crc = calculateCrc16Ccitt(raw, startIndex = 0, length = totalSize - 2)
    val footerBuffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    footerBuffer.position(totalSize - 2)
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
