package com.grid.cosrayapp.data.telemetry

import com.grid.cosrayapp.domain.model.DetectorId
import com.grid.cosrayapp.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FirmwarePacketMapperIdTest {
  @Test
  fun `build telemetry id should be stable for same input`() {
    val detectorId = DetectorId("AA:BB:CC:DD:EE:FF")

    val first =
      FirmwarePacketMapper.buildTelemetryId(
        detectorId = detectorId,
        packetType = PacketType.TIMELINE,
        packageCounter = 100,
        eventIndex = 0,
        eventTimestampMillis = 1_774_086_272_000L,
        cpuTime = 123_456L,
      )
    val second =
      FirmwarePacketMapper.buildTelemetryId(
        detectorId = detectorId,
        packetType = PacketType.TIMELINE,
        packageCounter = 100,
        eventIndex = 0,
        eventTimestampMillis = 1_774_086_272_000L,
        cpuTime = 123_456L,
      )

    assertEquals(first, second)
  }

  @Test
  fun `build telemetry id should differ across packet counter and event index`() {
    val detectorId = DetectorId("AA:BB:CC:DD:EE:FF")

    val base =
      FirmwarePacketMapper.buildTelemetryId(
        detectorId = detectorId,
        packetType = PacketType.TIMELINE,
        packageCounter = 100,
        eventIndex = 0,
        eventTimestampMillis = 1_774_086_272_000L,
        cpuTime = 123_456L,
      )
    val differentPacket =
      FirmwarePacketMapper.buildTelemetryId(
        detectorId = detectorId,
        packetType = PacketType.TIMELINE,
        packageCounter = 101,
        eventIndex = 0,
        eventTimestampMillis = 1_774_086_272_000L,
        cpuTime = 123_456L,
      )
    val differentEvent =
      FirmwarePacketMapper.buildTelemetryId(
        detectorId = detectorId,
        packetType = PacketType.TIMELINE,
        packageCounter = 100,
        eventIndex = 1,
        eventTimestampMillis = 1_774_086_272_000L,
        cpuTime = 123_456L,
      )

    assertNotEquals(base, differentPacket)
    assertNotEquals(base, differentEvent)
  }
}
