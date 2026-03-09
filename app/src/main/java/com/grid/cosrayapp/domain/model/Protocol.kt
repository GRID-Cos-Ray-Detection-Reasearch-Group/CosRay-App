@file:Suppress("MagicNumber")

package com.grid.cosrayapp.domain.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Protocol {
  private const val CRC16_POLY = 0x1021
  private const val CRC16_INIT = 0xFFFF

  private fun calculateCrc16Ccitt(data: ByteArray, startIndex: Int, length: Int): Int {
    var crc = CRC16_INIT
    for (index in startIndex until startIndex + length) {
      crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
      repeat(8) {
        crc =
                if ((crc and 0x8000) != 0) {
                  ((crc shl 1) xor CRC16_POLY) and 0xFFFF
                } else {
                  (crc shl 1) and 0xFFFF
                }
      }
    }
    return crc and 0xFFFF
  }

  // 指令帧定义（App → ESP32）
  object Command {
    const val OPCODE_START: Byte = 0x01
    const val OPCODE_STOP: Byte = 0x02
    const val OPCODE_ACK: Byte = 0x03
    const val OPCODE_NACK: Byte = 0x04
    const val OPCODE_STATUS: Byte = 0x05
    const val OPCODE_PING: Byte = 0x06

    const val TYPE_NONE: Byte = 0x00
    const val TYPE_MUON: Byte = 0x01
    const val TYPE_TIMELINE: Byte = 0x02

    private const val COMMAND_LENGTH = 8
    private const val COMMAND_PAYLOAD_LENGTH = 6
    private const val COMMAND_PACKAGE_LENGTH = 10

    fun buildStartCommand(packageId: Long = 0, packetType: Byte = TYPE_MUON): ByteArray =
            buildCommand(OPCODE_START, packageId, packetType)

    fun buildStopCommand(): ByteArray = buildCommand(OPCODE_STOP)

    fun buildAckCommand(packageId: Long, packetType: Byte): ByteArray =
            buildCommand(OPCODE_ACK, packageId, packetType)

    fun buildNackCommand(packageId: Long, packetType: Byte): ByteArray =
            buildCommand(OPCODE_NACK, packageId, packetType)

    fun buildStatusCommand(): ByteArray = buildCommand(OPCODE_STATUS)

    fun buildPingCommand(): ByteArray = buildCommand(OPCODE_PING)

    fun buildRequestFrame(cmdType: Byte = TYPE_MUON): ByteArray =
            buildStartCommand(packetType = cmdType)

    private fun buildCommand(
            opcode: Byte,
            packageId: Long = 0,
            packetType: Byte = TYPE_NONE,
    ): ByteArray {
      require(packageId in 0..0xFFFF_FFFFL) { "packageId 必须在 uint32 范围内" }

      val command = ByteArray(COMMAND_LENGTH)
      command[0] = opcode
      command[1] = ((packageId shr 24) and 0xFF).toByte()
      command[2] = ((packageId shr 16) and 0xFF).toByte()
      command[3] = ((packageId shr 8) and 0xFF).toByte()
      command[4] = (packageId and 0xFF).toByte()
      command[5] = packetType

      val crc = calculateCrc16Ccitt(command, startIndex = 0, length = COMMAND_PAYLOAD_LENGTH)
      return ByteArray(COMMAND_PACKAGE_LENGTH).also { bytes ->
        command.copyInto(bytes, endIndex = COMMAND_LENGTH)
        bytes[COMMAND_LENGTH] = (crc and 0xFF).toByte()
        bytes[COMMAND_LENGTH + 1] = ((crc shr 8) and 0xFF).toByte()
      }
    }
  }

  // 数据包结构（ESP32 → App）：镜像firmware里的typedefs.h

  data class MuonData(
          val cpuTime: Long, // CPU时钟（8字节，lifetime counter）
          val energy: Short, // μ子能量（2字节，16位ADC测量值）
          val pps: Int, // 上电以来PPS脉冲计数（4字节）
  ) {
    companion object {
      const val SIZE = 14

      fun fromByteBuffer(buffer: ByteBuffer): MuonData {
        require(buffer.order() == ByteOrder.LITTLE_ENDIAN) { "MuonData 解析需小端序" }
        return MuonData(
                cpuTime = buffer.long, // 读取8字节uint64_t
                energy = buffer.short, // 读取2字节uint16_t
                pps = buffer.int, // 读取4字节uint32_t
        )
      }
    }
  }

  data class MuonDataPkg(
          val head: ByteArray, // 包头部标识（3字节：0xAA,0xBB,0xCC）
          val pkgCnt: Int, // 全局数据包计数（4字节，掉电不丢失）
          val utc: Int, // 包第一个计数的UTC时间（4字节）
          val muonDataList: List<MuonData>, // 35个μ子事件（35×14=490字节）
          val tail: ByteArray, // 包尾部标识（3字节：0xDD,0xEE,0xFF）
          val crc: Short, // 校验和（2字节）
          val reserved: ByteArray, // 预留字段（6字节）
  ) {
    companion object {
      const val TOTAL_SIZE = 512 // 数据包总字节数（与固件一致）
      private val HEAD_EXPECTED = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()) // 预期头部标识
      private val TAIL_EXPECTED = byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()) // 预期尾部标识

      /**
       * 从原始字节流解析 MuonDataPkg
       *
       * @param rawData ESP32发送的512字节原始数据
       * @return 解析后的 MuonDataPkg 对象（解析失败抛出异常）
       */
      fun fromRawData(rawData: ByteArray): MuonDataPkg {
        require(rawData.size == TOTAL_SIZE) {
          "μ子数据包长度错误：预期${TOTAL_SIZE}字节，实际${rawData.size}字节"
        }
        val buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

        // Extract exact payload size for 512 bytes struct
        val itemCount = 35

        val head = ByteArray(3).also { buffer.get(it) }
        require(head.contentEquals(HEAD_EXPECTED)) {
          "μ子数据包头部标识错误：预期${HEAD_EXPECTED.contentToString()}，实际${head.contentToString()}"
        }

        val pkgCnt = buffer.int
        val utc = buffer.int

        val muonDataList = mutableListOf<MuonData>()
        repeat(itemCount) { muonDataList.add(MuonData.fromByteBuffer(buffer)) }

        val tail = ByteArray(3).also { buffer.get(it) }
        require(tail.contentEquals(TAIL_EXPECTED)) {
          "μ子数据包尾部标识错误：预期${TAIL_EXPECTED.contentToString()}，实际${tail.contentToString()}"
        }

        val reserved =
                ByteArray(6).also {
                  if (buffer.remaining() >= 6) {
                    buffer.get(it)
                  } else if (buffer.remaining() > 0) {
                    // Copy what's available
                    buffer.get(it, 0, buffer.remaining())
                  }
                }

        val crc = buffer.short

        // Calculate CRC
        // The firmware computes CRC starting from index 0 for length = TOTAL_SIZE - 2
        val calculatedCrc =
                calculateCrc16Ccitt(
                        data = rawData,
                        startIndex = 0,
                        length = TOTAL_SIZE - 2,
                )
        require((crc.toInt() and 0xFFFF) == calculatedCrc) {
          "μ子数据包CRC校验失败：预期${crc.toInt() and 0xFFFF}，实际$calculatedCrc"
        }

        return MuonDataPkg(head, pkgCnt, utc, muonDataList, tail, crc, reserved)
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as MuonDataPkg
      return head.contentEquals(other.head) &&
              pkgCnt == other.pkgCnt &&
              utc == other.utc &&
              muonDataList == other.muonDataList &&
              tail.contentEquals(other.tail) &&
              crc == other.crc &&
              reserved.contentEquals(other.reserved)
    }

    override fun hashCode(): Int {
      var result = head.contentHashCode()
      result = 31 * result + pkgCnt
      result = 31 * result + utc
      result = 31 * result + muonDataList.hashCode()
      result = 31 * result + tail.contentHashCode()
      result = 31 * result + crc
      result = 31 * result + reserved.contentHashCode()
      return result
    }

    override fun toString(): String =
            "MuonDataPkg(pkgCnt=$pkgCnt, utc=$utc, muonCount=${muonDataList.size}, " +
                    "head=${head.contentToString()}, tail=${tail.contentToString()})"
  }

  data class TimeLineData(
          val cpuTime: Long, // 写入数据时的CPU时钟（8字节）
          val pps: Int, // 当前PPS脉冲计数（4字节）
          val utc: Int, // 最近一次UTC时间戳（4字节）
          val ppsUtc: Int, // 上次记录UTC时的PPS计数（4字节）
          val cpuTimePps: Long, // 上次收到PPS脉冲时的CPU时钟（8字节）
          val gpsLong: Int, // GPS经度（4字节，1m级，0°-360°）
          val gpsLat: Int, // GPS纬度（4字节，1m级，-90°-90°）
          val gpsAlt: Short, // GPS海拔（2字节，1m级）
          val accX: Byte, // 加速度X轴（1字节）
          val accY: Byte, // 加速度Y轴（1字节）
          val accZ: Byte, // 加速度Z轴（1字节）
          val siPMTmp: Short, // SiPM附近温度（2字节，tmp112测量）
          val mcUTmp: Byte, // ESP32内置温度（1字节）
          val siPMImon: Short, // SiPM漏电流监测（2字节）
          val siPMVmon: Short, // SiPM偏压监测（2字节）
  ) {
    companion object {
      const val SIZE = 48 // 结构体总字节数（与固件一致）

      fun fromByteBuffer(buffer: ByteBuffer): TimeLineData {
        require(buffer.order() == ByteOrder.LITTLE_ENDIAN) { "TimeLineData 解析需小端序" }
        return TimeLineData(
                cpuTime = buffer.long,
                pps = buffer.int,
                utc = buffer.int,
                ppsUtc = buffer.int,
                cpuTimePps = buffer.long,
                gpsLong = buffer.int,
                gpsLat = buffer.int,
                gpsAlt = buffer.short,
                accX = buffer.get(),
                accY = buffer.get(),
                accZ = buffer.get(),
                siPMTmp = buffer.short,
                mcUTmp = buffer.get(),
                siPMImon = buffer.short,
                siPMVmon = buffer.short,
        )
      }
    }
  }

  data class TimeLinePkg(
          val head: ByteArray, // 包头部标识（3字节：0x12,0x34,0x56）
          val pkgCnt: Int, // 全局数据包计数（4字节，掉电不丢失）
          val timeLineDataList: List<TimeLineData>, // 10个时间线事件（10×48=480字节）
          val tail: ByteArray, // 包尾部标识（3字节：0x78,0x9A,0xBC）
          val crc: Short, // 校验和（2字节）
          val reserve: ByteArray, // 预留字段（20字节）
  ) {
    companion object {
      const val TOTAL_SIZE = 512 // 数据包总字节数（与固件一致）
      private val HEAD_EXPECTED = byteArrayOf(0x12, 0x34, 0x56) // 预期头部标识
      private val TAIL_EXPECTED = byteArrayOf(0x78.toByte(), 0x9A.toByte(), 0xBC.toByte()) // 预期尾部标识

      fun fromRawData(rawData: ByteArray): TimeLinePkg {
        require(rawData.size == TOTAL_SIZE) {
          "时间线数据包长度错误：预期${TOTAL_SIZE}字节，实际${rawData.size}字节"
        }
        val buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

        // Exact number of items based on fixed 512 byte struct
        val itemCount = 10

        val head = ByteArray(3).also { buffer.get(it) }
        require(head.contentEquals(HEAD_EXPECTED)) {
          "时间线数据包头部标识错误：预期${HEAD_EXPECTED.contentToString()}，实际${head.contentToString()}"
        }

        val pkgCnt = buffer.int

        val timeLineDataList = mutableListOf<TimeLineData>()
        repeat(itemCount) { timeLineDataList.add(TimeLineData.fromByteBuffer(buffer)) }

        val tail = ByteArray(3).also { buffer.get(it) }
        require(tail.contentEquals(TAIL_EXPECTED)) {
          "时间线数据包尾部标识错误：预期${TAIL_EXPECTED.contentToString()}，实际${tail.contentToString()}"
        }

        val reserve =
                ByteArray(20).also {
                  if (buffer.remaining() >= 20) {
                    buffer.get(it)
                  } else if (buffer.remaining() > 0) {
                    // Copy what's available
                    buffer.get(it, 0, buffer.remaining())
                  }
                }

        val crc = buffer.short

        // Calculate CRC
        // The firmware computes CRC starting from index 0 for length = TOTAL_SIZE - 2
        val calculatedCrc =
                calculateCrc16Ccitt(
                        data = rawData,
                        startIndex = 0,
                        length = TOTAL_SIZE - 2,
                )
        require((crc.toInt() and 0xFFFF) == calculatedCrc) {
          "时间线数据包CRC校验失败：预期${crc.toInt() and 0xFFFF}，实际$calculatedCrc"
        }

        return TimeLinePkg(head, pkgCnt, timeLineDataList, tail, crc, reserve)
      }
    }

    // 重写equals和hashCode
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as TimeLinePkg
      return head.contentEquals(other.head) &&
              pkgCnt == other.pkgCnt &&
              timeLineDataList == other.timeLineDataList &&
              tail.contentEquals(other.tail) &&
              crc == other.crc &&
              reserve.contentEquals(other.reserve)
    }

    override fun hashCode(): Int {
      var result = head.contentHashCode()
      result = 31 * result + pkgCnt
      result = 31 * result + timeLineDataList.hashCode()
      result = 31 * result + tail.contentHashCode()
      result = 31 * result + crc
      result = 31 * result + reserve.contentHashCode()
      return result
    }

    // 打印数据包信息
    override fun toString(): String =
            "TimeLinePkg(pkgCnt=$pkgCnt, eventCount=${timeLineDataList.size}, " +
                    "head=${head.contentToString()}, tail=${tail.contentToString()})"
  }
}
