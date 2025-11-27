package com.travellerse.cosray_app.domain.mapper

import com.travellerse.cosray_app.core.network.model.MuonEventDto
import com.travellerse.cosray_app.core.network.model.MuonPacketDto
import com.travellerse.cosray_app.core.network.model.PacketUploadRequest
import com.travellerse.cosray_app.core.network.model.TimelineEventDto
import com.travellerse.cosray_app.core.network.model.TimelinePacketDto
import com.travellerse.cosray_app.domain.model.Protocol

/**
 * 将Protocol数据类映射到API DTO
 */
object ProtocolMapper {

    /**
     * 将MuonData转换为MuonEventDto
     */
    fun toMuonEventDto(muonData: Protocol.MuonData): MuonEventDto {
        return MuonEventDto(
            cpuTime = muonData.cpuTime,
            energy = muonData.energy.toInt() and 0xFFFF,  // 转为unsigned
            pps = muonData.pps.toLong() and 0xFFFFFFFF  // 转为unsigned
        )
    }

    /**
     * 将MuonDataPkg转换为MuonPacketDto
     */
    fun toMuonPacketDto(pkg: Protocol.MuonDataPkg): MuonPacketDto {
        return MuonPacketDto(
            packageCounter = pkg.pkgCnt.toLong() and 0xFFFFFFFF,  // 转为unsigned
            utc = pkg.utc.toLong() and 0xFFFFFFFF,
            events = pkg.muonDataList.map { toMuonEventDto(it) },
            head = pkg.head.map { it.toInt() and 0xFF },  // 转为unsigned byte
            tail = pkg.tail.map { it.toInt() and 0xFF },
            crc = pkg.crc.toInt() and 0xFFFF,
            reserved = pkg.reserved.map { it.toInt() and 0xFF }
        )
    }

    /**
     * 将TimeLineData转换为TimelineEventDto
     */
    fun toTimelineEventDto(data: Protocol.TimeLineData): TimelineEventDto {
        return TimelineEventDto(
            cpuTime = data.cpuTime,
            pps = data.pps.toLong() and 0xFFFFFFFF,
            utc = data.utc.toLong() and 0xFFFFFFFF,
            ppsUtc = data.ppsUtc.toLong() and 0xFFFFFFFF,
            cputimePps = data.cpuTimePps,
            gpsLong = data.gpsLong,
            gpsLat = data.gpsLat,
            gpsAlt = data.gpsAlt.toInt(),
            accX = data.accX.toInt(),
            accY = data.accY.toInt(),
            accZ = data.accZ.toInt(),
            siPMTmp = data.siPMTmp.toInt() and 0xFFFF,
            mcuTmp = data.mcUTmp.toInt() and 0xFF,
            siPMImon = data.siPMImon.toInt() and 0xFFFF,
            siPMVmon = data.siPMVmon.toInt() and 0xFFFF
        )
    }

    /**
     * 将TimeLinePkg转换为TimelinePacketDto
     */
    fun toTimelinePacketDto(pkg: Protocol.TimeLinePkg): TimelinePacketDto {
        return TimelinePacketDto(
            packageCounter = pkg.pkgCnt.toLong() and 0xFFFFFFFF,
            events = pkg.timeLineDataList.map { toTimelineEventDto(it) },
            head = pkg.head.map { it.toInt() and 0xFF },
            tail = pkg.tail.map { it.toInt() and 0xFF },
            crc = pkg.crc.toInt() and 0xFFFF,
            reserved = pkg.reserve.map { it.toInt() and 0xFF }
        )
    }

    /**
     * 创建Muon数据包上传请求
     */
    fun createMuonPacketRequest(
        macAddress: String,
        pkg: Protocol.MuonDataPkg
    ): PacketUploadRequest {
        return PacketUploadRequest(
            device = macAddress,
            packetType = "muon",
            muonPacket = toMuonPacketDto(pkg)
        )
    }

    /**
     * 创建Timeline数据包上传请求
     */
    fun createTimelinePacketRequest(
        macAddress: String,
        pkg: Protocol.TimeLinePkg
    ): PacketUploadRequest {
        return PacketUploadRequest(
            device = macAddress,
            packetType = "timeline",
            timelinePacket = toTimelinePacketDto(pkg)
        )
    }
}
