package com.grid.cosrayapp.data.telemetry

import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.domain.mapper.ProtocolMapper
import com.grid.cosrayapp.domain.model.AccelerationSnapshot
import com.grid.cosrayapp.domain.model.AcquisitionMetrics
import com.grid.cosrayapp.domain.model.DetectorId
import com.grid.cosrayapp.domain.model.EnvironmentSnapshot
import com.grid.cosrayapp.domain.model.LocationSnapshot
import com.grid.cosrayapp.domain.model.PacketMetadata
import com.grid.cosrayapp.domain.model.PacketType
import com.grid.cosrayapp.domain.model.PowerSnapshot
import com.grid.cosrayapp.domain.model.Protocol
import com.grid.cosrayapp.domain.model.RadiationMetrics
import com.grid.cosrayapp.domain.model.SipmMonitoring
import com.grid.cosrayapp.domain.model.TelemetryId
import com.grid.cosrayapp.domain.model.TelemetrySample
import java.time.Instant
import java.util.Locale

data class ParsedFirmwarePacket(
        val uploadRequest: PacketUploadRequest,
        val samples: List<TelemetrySample>,
)

internal object FirmwarePacketMapper {
    private const val UINT32_MAX = 4294967295.0
    private const val INT32_MAX = 2147483647.0

    fun parse(macAddress: String, packetBytes: ByteArray): ParsedFirmwarePacket? {
        if (packetBytes.size < HEADER_SIZE) return null

        return when {
            hasHeader(packetBytes, MUON_HEAD) -> parseMuonPacket(macAddress, packetBytes)
            hasHeader(packetBytes, TIMELINE_HEAD) -> parseTimelinePacket(macAddress, packetBytes)
            else -> null
        }
    }

    private fun parseMuonPacket(macAddress: String, packetBytes: ByteArray): ParsedFirmwarePacket? {
        val packet =
                runCatching { Protocol.MuonDataPkg.fromRawData(packetBytes) }.getOrNull()
                        ?: return null
        val samples = muonPacketSamples(macAddress, packet)
        if (samples.isEmpty()) return null
        return ParsedFirmwarePacket(
                uploadRequest = ProtocolMapper.createMuonPacketRequest(macAddress, packet),
                samples = samples,
        )
    }

    private fun parseTimelinePacket(
            macAddress: String,
            packetBytes: ByteArray
    ): ParsedFirmwarePacket? {
        val packet =
                runCatching { Protocol.TimeLinePkg.fromRawData(packetBytes) }.getOrNull()
                        ?: return null
        val samples = timelinePacketSamples(macAddress, packet)
        if (samples.isEmpty()) return null
        return ParsedFirmwarePacket(
                uploadRequest = ProtocolMapper.createTimelinePacketRequest(macAddress, packet),
                samples = samples,
        )
    }

    private fun muonPacketSamples(
            macAddress: String,
            packet: Protocol.MuonDataPkg,
    ): List<TelemetrySample> {
        val detectorId = DetectorId(macAddress)
        val packetUtcMillis = (packet.utc.toLong() and 0xFFFFFFFFL) * 1000L
        val validEvents = ProtocolMapper.meaningfulMuonEvents(packet)

        return validEvents.mapIndexed { index, event ->
            val eventTimestampMillis = packetUtcMillis + index
            TelemetrySample(
                    id =
                            buildTelemetryId(
                                    detectorId = detectorId,
                                    packetType = PacketType.MUON,
                                    packageCounter = packet.pkgCnt,
                                    eventIndex = index,
                                    eventTimestampMillis = eventTimestampMillis,
                                    cpuTime = event.cpuTime,
                            ),
                    detectorId = detectorId,
                    recordedAt = Instant.ofEpochMilli(eventTimestampMillis),
                    acquisition =
                            AcquisitionMetrics(particleCount = event.energy.toInt() and 0xFFFF),
                    radiation = RadiationMetrics(doseRateMicrosievertsPerHour = 0.0),
                    environment = EnvironmentSnapshot(),
                    power = PowerSnapshot(),
                    packetMetadata =
                            PacketMetadata(
                                    packetType = PacketType.MUON,
                                    packageCounter = packet.pkgCnt,
                                    cpuTime = event.cpuTime,
                                    pps = event.pps,
                                    utcTimestamp = packetUtcMillis,
                                    crc = packet.crc.toInt() and 0xFFFF,
                                    eventIndex = index + 1,
                                    eventCount = validEvents.size,
                            ),
            )
        }
    }

    private fun timelinePacketSamples(
            macAddress: String,
            packet: Protocol.TimeLinePkg,
    ): List<TelemetrySample> {
        val detectorId = DetectorId(macAddress)
        val validEvents = ProtocolMapper.meaningfulTimelineEvents(packet)

        return validEvents.mapIndexed { index, event ->
            val eventUtcMillis = (event.utc.toLong() and 0xFFFFFFFFL) * 1000L
            val eventTimestampMillis = eventUtcMillis + index
            TelemetrySample(
                    id =
                            buildTelemetryId(
                                    detectorId = detectorId,
                                    packetType = PacketType.TIMELINE,
                                    packageCounter = packet.pkgCnt,
                                    eventIndex = index,
                                    eventTimestampMillis = eventTimestampMillis,
                                    cpuTime = event.cpuTime,
                            ),
                    detectorId = detectorId,
                    recordedAt = Instant.ofEpochMilli(eventTimestampMillis),
                    acquisition = AcquisitionMetrics(particleCount = 0),
                    radiation = RadiationMetrics(doseRateMicrosievertsPerHour = 0.0),
                    environment =
                            EnvironmentSnapshot(
                                    sipmTemperatureCelsius =
                                            (event.siPMTmp.toInt() and 0xFFFF).toDouble(),
                                    mcuTemperatureCelsius =
                                            (event.mcUTmp.toInt() and 0xFF).toDouble(),
                            ),
                    power = PowerSnapshot(),
                    location =
                            LocationSnapshot(
                                    longitudeDegrees = scaleLongitude(event.gpsLong),
                                    latitudeDegrees = scaleLatitude(event.gpsLat),
                                    altitudeMeters = event.gpsAlt.toInt(),
                            ),
                    acceleration =
                            AccelerationSnapshot(
                                    xAxis = event.accX.toInt(),
                                    yAxis = event.accY.toInt(),
                                    zAxis = event.accZ.toInt(),
                            ),
                    sipmMonitoring =
                            SipmMonitoring(
                                    currentMicroamps = event.siPMImon.toInt() and 0xFFFF,
                                    voltageMillivolts = event.siPMVmon.toInt() and 0xFFFF,
                            ),
                    packetMetadata =
                            PacketMetadata(
                                    packetType = PacketType.TIMELINE,
                                    packageCounter = packet.pkgCnt,
                                    cpuTime = event.cpuTime,
                                    pps = event.pps,
                                    utcTimestamp = eventUtcMillis,
                                    crc = packet.crc.toInt() and 0xFFFF,
                                    eventIndex = index + 1,
                                    eventCount = validEvents.size,
                            ),
            )
        }
    }

    private fun scaleLongitude(rawLongitude: Int): Double {
        val normalized = rawLongitude.toLong() and 0xFFFFFFFFL
        return normalized * 360.0 / UINT32_MAX
    }

    private fun scaleLatitude(rawLatitude: Int): Double = rawLatitude * 90.0 / INT32_MAX

    internal fun buildTelemetryId(
            detectorId: DetectorId,
            packetType: PacketType,
            packageCounter: Int,
            eventIndex: Int,
            eventTimestampMillis: Long,
            cpuTime: Long,
    ): TelemetryId {
        val seed =
                "%s:%s:%d:%d:%d:%d"
                        .format(
                                Locale.US,
                                detectorId.value,
                                packetType.name,
                                packageCounter,
                                eventIndex,
                                eventTimestampMillis,
                                cpuTime,
                        )
        return TelemetryId(java.util.UUID.nameUUIDFromBytes(seed.toByteArray()).toString())
    }

    private fun hasHeader(packetBytes: ByteArray, header: ByteArray): Boolean =
            packetBytes[0] == header[0] &&
                    packetBytes[1] == header[1] &&
                    packetBytes[2] == header[2]

    private const val HEADER_SIZE = 3
    private val MUON_HEAD = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
    private val TIMELINE_HEAD = byteArrayOf(0x12, 0x34, 0x56)
}
