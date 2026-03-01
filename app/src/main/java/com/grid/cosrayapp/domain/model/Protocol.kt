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
    // 帧头/帧尾
    const val FRAME_HEADER: Short = 0x55AA.toShort()
    const val FRAME_TRAILER: Short = 0xAA55.toShort()

    const val CMD_TYPE_REQUEST_DATA: Byte = 0x01
    const val CMD_ID_REQUEST_DATA: Byte = 0x01

    fun buildRequestFrame(cmdType: Byte = CMD_TYPE_REQUEST_DATA): ByteArray {
      val paramLength = 0 // 当前指令无参数，长度为0
      // 总长度计算：帧头(2) + 指令类型(1) + 指令ID(1) + 预留参数长度(2) + 参数(0) + 校验和(1) + 帧尾(2)
      val totalLength = 2 + 1 + 1 + 2 + paramLength + 1 + 2

      val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN) // 指令帧默认大端序
      buffer.putShort(FRAME_HEADER)
      buffer.put(cmdType)
      buffer.put(CMD_ID_REQUEST_DATA)
      buffer.putShort(paramLength.toShort())
      val checksum =
        calculateChecksum(buffer.array(), startIndex = 2, length = 1 + 1 + 2 + paramLength)
      buffer.put(checksum)
      buffer.putShort(FRAME_TRAILER)

      return buffer.array()
    }

    internal fun calculateChecksum(data: ByteArray, startIndex: Int, length: Int): Byte {
      var checksum: Byte = 0
      for (i in startIndex until startIndex + length) {
        checksum = (checksum.toInt() xor data[i].toInt()).toByte()
      }
      return checksum
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
        require(rawData.size == TOTAL_SIZE) { "μ子数据包长度错误：预期512字节，实际${rawData.size}字节" }
        val buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

        val head = ByteArray(3).also { buffer.get(it) }
        require(head.contentEquals(HEAD_EXPECTED)) {
          "μ子数据包头部标识错误：预期${HEAD_EXPECTED.contentToString()}，实际${head.contentToString()}"
        }

        val pkgCnt = buffer.int
        val utc = buffer.int

        val muonDataList = mutableListOf<MuonData>()
        repeat(35) { muonDataList.add(MuonData.fromByteBuffer(buffer)) }

        val tail = ByteArray(3).also { buffer.get(it) }
        require(tail.contentEquals(TAIL_EXPECTED)) {
          "μ子数据包尾部标识错误：预期${TAIL_EXPECTED.contentToString()}，实际${tail.contentToString()}"
        }

        val reserved = ByteArray(6).also { buffer.get(it) }
        val crc = buffer.short
        val calculatedCrc =
          calculateCrc16Ccitt(
            data = rawData,
            startIndex = 3,
            length = TOTAL_SIZE - 3 - 2,
          ) // Skip 3 byte header, 2 byte CRC
        require((crc.toInt() and 0xFFFF) == calculatedCrc) {
          "μ子数据包CRC校验失败：预期${crc.toInt() and 0xFFFF}，实际$calculatedCrc (旧算法偏移量为3起算)"
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
        require(rawData.size == TOTAL_SIZE) { "时间线数据包长度错误：预期512字节，实际${rawData.size}字节" }

        val buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

        val head = ByteArray(3).also { buffer.get(it) }
        require(head.contentEquals(HEAD_EXPECTED)) {
          "时间线数据包头部标识错误：预期${HEAD_EXPECTED.contentToString()}，实际${head.contentToString()}"
        }

        val pkgCnt = buffer.int

        val timeLineDataList = mutableListOf<TimeLineData>()
        repeat(10) { timeLineDataList.add(TimeLineData.fromByteBuffer(buffer)) }

        val tail = ByteArray(3).also { buffer.get(it) }
        require(tail.contentEquals(TAIL_EXPECTED)) {
          "时间线数据包尾部标识错误：预期${TAIL_EXPECTED.contentToString()}，实际${tail.contentToString()}"
        }

        val reserve = ByteArray(20).also { buffer.get(it) }
        val crc = buffer.short
        val calculatedCrc =
          calculateCrc16Ccitt(
            data = rawData,
            startIndex = 3,
            length = TOTAL_SIZE - 3 - 2,
          ) // Skip 3 byte header, 2 byte CRC
        require((crc.toInt() and 0xFFFF) == calculatedCrc) {
          "时间线数据包CRC校验失败：预期${crc.toInt() and 0xFFFF}，实际$calculatedCrc (旧算法偏移量为3起算)"
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
