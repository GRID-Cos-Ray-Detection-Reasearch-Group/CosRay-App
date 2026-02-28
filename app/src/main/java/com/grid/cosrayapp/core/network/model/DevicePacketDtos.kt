package com.grid.cosrayapp.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 设备DTO */
@Serializable
data class DeviceDto(
  @SerialName("id") val id: Int,
  @SerialName("mac_address") val macAddress: String,
  @SerialName("name") val name: String,
  @SerialName("description") val description: String? = null,
  @SerialName("is_active") val isActive: Boolean,
  @SerialName("owner_id") val ownerId: Int,
  @SerialName("owner_username") val ownerUsername: String,
  @SerialName("created_at") val createdAt: String,
  @SerialName("updated_at") val updatedAt: String,
  @SerialName("last_seen_at") val lastSeenAt: String?,
)

/** 设备更新请求 */
@Serializable
data class UpdateDeviceRequest(
  @SerialName("name") val name: String? = null,
  @SerialName("description") val description: String? = null,
  @SerialName("is_active") val isActive: Boolean? = null,
)

/** μ子事件DTO */
@Serializable
data class MuonEventDto(
  @SerialName("cpu_time") val cpuTime: Long,
  @SerialName("energy") val energy: Int,
  @SerialName("pps") val pps: Long,
  @SerialName("timestamp") val timestamp: Long? = null,
)

/** μ子数据包DTO */
@Serializable
data class MuonPacketDto(
  @SerialName("package_counter") val packageCounter: Long,
  @SerialName("utc") val utc: Long,
  @SerialName("events") val events: List<MuonEventDto>,
  @SerialName("head") val head: List<Int>? = null,
  @SerialName("tail") val tail: List<Int>? = null,
  @SerialName("crc") val crc: Int? = null,
  @SerialName("reserved") val reserved: List<Int>? = null,
)

/** 时间线事件DTO */
@Serializable
data class TimelineEventDto(
  @SerialName("cpu_time") val cpuTime: Long,
  @SerialName("pps") val pps: Long,
  @SerialName("utc") val utc: Long,
  @SerialName("pps_utc") val ppsUtc: Long,
  @SerialName("cputime_pps") val cputimePps: Long,
  @SerialName("gps_long") val gpsLong: Int,
  @SerialName("gps_lat") val gpsLat: Int,
  @SerialName("gps_alt") val gpsAlt: Int,
  @SerialName("acc_x") val accX: Int,
  @SerialName("acc_y") val accY: Int,
  @SerialName("acc_z") val accZ: Int,
  @SerialName("sipm_tmp") val sipmTmp: Int,
  @SerialName("mcu_tmp") val mcuTmp: Int,
  @SerialName("sipm_imon") val sipmImon: Int,
  @SerialName("sipm_vmon") val sipmVmon: Int,
  @SerialName("timestamp") val timestamp: Long? = null,
)

/** 时间线数据包DTO */
@Serializable
data class TimelinePacketDto(
  @SerialName("package_counter") val packageCounter: Long,
  @SerialName("events") val events: List<TimelineEventDto>,
  @SerialName("head") val head: List<Int>? = null,
  @SerialName("tail") val tail: List<Int>? = null,
  @SerialName("crc") val crc: Int? = null,
  @SerialName("reserved") val reserved: List<Int>? = null,
)

/** 数据包上传请求 */
@Serializable
data class PacketUploadRequest(
  @SerialName("device") val device: String, // MAC地址
  @SerialName("packet_type") val packetType: String, // "muon" or "timeline"
  @SerialName("muon_packet") val muonPacket: MuonPacketDto? = null,
  @SerialName("timeline_packet") val timelinePacket: TimelinePacketDto? = null,
)

/** 数据包上传响应 */
@Serializable
data class PacketUploadResponse(
  @SerialName("device") val device: String,
  @SerialName("device_name") val deviceName: String? = null,
  @SerialName("packet_type") val packetType: String,
  @SerialName("records_written") val recordsWritten: Int,
  @SerialName("message") val message: String? = null,
)
