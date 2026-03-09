@file:Suppress("MagicNumber")

package com.grid.cosrayapp.domain.mapper

import com.grid.cosrayapp.core.network.model.MuonEventDto
import com.grid.cosrayapp.core.network.model.MuonPacketDto
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.TimelineEventDto
import com.grid.cosrayapp.core.network.model.TimelinePacketDto
import com.grid.cosrayapp.domain.model.Protocol

/** 将Protocol数据类映射到API DTO */
object ProtocolMapper {
  fun meaningfulMuonEvents(pkg: Protocol.MuonDataPkg): List<Protocol.MuonData> =
          pkg.muonDataList.filter(::isMeaningfulMuonEvent)

  fun meaningfulTimelineEvents(pkg: Protocol.TimeLinePkg): List<Protocol.TimeLineData> =
          pkg.timeLineDataList.filter(::isMeaningfulTimelineEvent)

  /** 将MuonData转换为MuonEventDto */
  fun toMuonEventDto(muonData: Protocol.MuonData): MuonEventDto =
          MuonEventDto(
                  cpuTime = muonData.cpuTime,
                  energy = muonData.energy.toInt() and 0xFFFF, // 转为unsigned
                  pps = muonData.pps.toLong() and 0xFFFFFFFF, // 转为unsigned
          )

  /** 将MuonDataPkg转换为MuonPacketDto */
  fun toMuonPacketDto(pkg: Protocol.MuonDataPkg): MuonPacketDto =
          MuonPacketDto(
                  packageCounter = pkg.pkgCnt.toLong() and 0xFFFFFFFF, // 转为unsigned
                  utc = pkg.utc.toLong() and 0xFFFFFFFF,
                  events = meaningfulMuonEvents(pkg).map { toMuonEventDto(it) },
                  head = pkg.head.map { it.toInt() and 0xFF }, // 转为unsigned byte
                  tail = pkg.tail.map { it.toInt() and 0xFF },
                  crc = pkg.crc.toInt() and 0xFFFF,
                  reserved = pkg.reserved.map { it.toInt() and 0xFF },
          )

  /** 将TimeLineData转换为TimelineEventDto */
  fun toTimelineEventDto(data: Protocol.TimeLineData): TimelineEventDto =
          TimelineEventDto(
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
                  sipmTmp = data.siPMTmp.toInt() and 0xFFFF,
                  mcuTmp = data.mcUTmp.toInt() and 0xFF,
                  sipmImon = data.siPMImon.toInt() and 0xFFFF,
                  sipmVmon = data.siPMVmon.toInt() and 0xFFFF,
          )

  /** 将TimeLinePkg转换为TimelinePacketDto */
  fun toTimelinePacketDto(pkg: Protocol.TimeLinePkg): TimelinePacketDto =
          TimelinePacketDto(
                  packageCounter = pkg.pkgCnt.toLong() and 0xFFFFFFFF,
                  events = meaningfulTimelineEvents(pkg).map { toTimelineEventDto(it) },
                  head = pkg.head.map { it.toInt() and 0xFF },
                  tail = pkg.tail.map { it.toInt() and 0xFF },
                  crc = pkg.crc.toInt() and 0xFFFF,
                  reserved = pkg.reserve.map { it.toInt() and 0xFF },
          )

  private fun isMeaningfulMuonEvent(event: Protocol.MuonData): Boolean =
          event.cpuTime != 0L || event.energy.toInt() != 0 || event.pps != 0

  private fun isMeaningfulTimelineEvent(event: Protocol.TimeLineData): Boolean =
          event.cpuTime != 0L ||
                  event.pps != 0 ||
                  event.utc != 0 ||
                  event.ppsUtc != 0 ||
                  event.cpuTimePps != 0L ||
                  event.gpsLong != 0 ||
                  event.gpsLat != 0 ||
                  event.gpsAlt.toInt() != 0 ||
                  event.accX.toInt() != 0 ||
                  event.accY.toInt() != 0 ||
                  event.accZ.toInt() != 0 ||
                  event.siPMTmp.toInt() != 0 ||
                  event.mcUTmp.toInt() != 0 ||
                  event.siPMImon.toInt() != 0 ||
                  event.siPMVmon.toInt() != 0

  /** 创建Muon数据包上传请求 */
  fun createMuonPacketRequest(macAddress: String, pkg: Protocol.MuonDataPkg): PacketUploadRequest =
          PacketUploadRequest(
                  device = macAddress,
                  packetType = "muon",
                  muonPacket = toMuonPacketDto(pkg)
          )

  /** 创建Timeline数据包上传请求 */
  fun createTimelinePacketRequest(
          macAddress: String,
          pkg: Protocol.TimeLinePkg,
  ): PacketUploadRequest =
          PacketUploadRequest(
                  device = macAddress,
                  packetType = "timeline",
                  timelinePacket = toTimelinePacketDto(pkg),
          )
}
