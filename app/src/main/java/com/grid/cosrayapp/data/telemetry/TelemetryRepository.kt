package com.grid.cosrayapp.data.telemetry

import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.domain.mapper.ProtocolMapper
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.Protocol
import com.grid.cosrayapp.domain.model.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

class TelemetryRepository(
  private val api: CosRayApi,
  private val bleRepository: BleRepository,
  private val authRepository: AuthRepository,
  externalScope: CoroutineScope,
) {
  private val packetAssembler = FirmwarePacketAssembler()

  private val _buffer = MutableStateFlow<List<TelemetrySample>>(emptyList())
  val bufferedSamples: StateFlow<List<TelemetrySample>> = _buffer.asStateFlow()

  private val _uploadBuffer = MutableStateFlow<List<PacketUploadRequest>>(emptyList())

  private val _liveTelemetry = MutableStateFlow<List<TelemetrySample>>(emptyList())
  val liveTelemetry: StateFlow<List<TelemetrySample>> = _liveTelemetry.asStateFlow()

  private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
  val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

  init {
    externalScope.launch {
      bleRepository.telemetry.collect { sample: TelemetrySample ->
        _buffer.update { list: List<TelemetrySample> -> (list + sample).takeLast(BUFFER_SIZE) }
        _liveTelemetry.update { list: List<TelemetrySample> ->
          (list + sample).sortedByDescending(TelemetrySample::recordedAt).take(MAX_LIVE_SAMPLES)
        }
      }
    }
    externalScope.launch {
      bleRepository.connectionState.collect { state: BleConnectionState ->
        when (state) {
          is BleConnectionState.Connected -> _connectedDevice.value = state.device
          else -> _connectedDevice.value = null
        }
      }
    }

    externalScope.launch {
      bleRepository.rawPackets.collect { rawPacket ->
        val deviceMac = _connectedDevice.value?.macAddress ?: return@collect
        val requests = packetAssembler.consume(rawPacket.data, deviceMac)
        if (requests.isNotEmpty()) {
          _uploadBuffer.update { list -> (list + requests).takeLast(BUFFER_SIZE) }
        }
      }
    }
  }

  suspend fun uploadBufferedSamples(): CosRayResult<Unit> {
    val requests = _uploadBuffer.value
    return if (requests.isEmpty()) {
      CosRayResult.Success(Unit)
    } else {
      val uploadResult: CosRayResult<Unit> = runCosRayCatching {
        requests.forEach { request ->
          val tokenResult = authRepository.ensureValidToken()
          val accessToken =
            when (tokenResult) {
              is CosRayResult.Success -> tokenResult.data
              is CosRayResult.Error -> throw tokenResult.throwable
            }
          api.uploadPacket(accessToken, request)
        }
      }

      if (uploadResult is CosRayResult.Success) {
        _buffer.value = emptyList()
        _liveTelemetry.value = emptyList()
        _uploadBuffer.value = emptyList()
        CosRayResult.Success(Unit)
      } else {
        uploadResult
      }
    }
  }

  fun clearBuffer() {
    _buffer.value = emptyList()
    _uploadBuffer.value = emptyList()
    packetAssembler.reset()
  }

  companion object {
    private const val BUFFER_SIZE = 128
    private const val MAX_LIVE_SAMPLES = 30
  }
}

internal class FirmwarePacketAssembler {
  private val byteBuffer = Buffer()

  fun consume(chunk: ByteArray, macAddress: String): List<PacketUploadRequest> {
    if (chunk.isEmpty()) return emptyList()
    byteBuffer.write(chunk)

    val requests = mutableListOf<PacketUploadRequest>()
    @Suppress("LoopWithTooManyJumpStatements")
    while (true) {
      val startIndex = findPacketStartIndex()
      if (startIndex < 0) {
        shrinkTailForResync()
        break
      }

      if (startIndex > 0) {
        byteBuffer.skip(startIndex.toLong())
      }

      if (byteBuffer.size < PACKET_SIZE.toLong()) {
        break
      }

      val packetPreview = Buffer()
      byteBuffer.copyTo(packetPreview, 0, PACKET_SIZE.toLong())
      val packetBytes = packetPreview.readByteArray()
      val request = parsePacket(macAddress, packetBytes)
      if (request != null) {
        requests.add(request)
        byteBuffer.skip(PACKET_SIZE.toLong())
      } else {
        byteBuffer.skip(1)
      }
    }

    return requests
  }

  fun reset() {
    byteBuffer.clear()
  }

  private fun parsePacket(macAddress: String, packetBytes: ByteArray): PacketUploadRequest? {
    return when {
      hasHeader(packetBytes, MUON_HEAD) ->
        runCatching {
            val packet = Protocol.MuonDataPkg.fromRawData(packetBytes)
            ProtocolMapper.createMuonPacketRequest(macAddress, packet)
          }
          .getOrNull()
      hasHeader(packetBytes, TIMELINE_HEAD) ->
        runCatching {
            val packet = Protocol.TimeLinePkg.fromRawData(packetBytes)
            ProtocolMapper.createTimelinePacketRequest(macAddress, packet)
          }
          .getOrNull()
      else -> null
    }
  }

  private fun findPacketStartIndex(): Int {
    if (byteBuffer.size < HEADER_SIZE.toLong()) return -1
    val muonIndex = byteBuffer.indexOf(MUON_HEAD)
    val timelineIndex = byteBuffer.indexOf(TIMELINE_HEAD)
    return when {
      muonIndex >= 0L && timelineIndex >= 0L -> minOf(muonIndex, timelineIndex).toInt()
      muonIndex >= 0L -> muonIndex.toInt()
      timelineIndex >= 0L -> timelineIndex.toInt()
      else -> -1
    }
  }

  private fun shrinkTailForResync() {
    if (byteBuffer.size <= (HEADER_SIZE - 1).toLong()) return
    val keep = (HEADER_SIZE - 1).toLong()
    byteBuffer.skip(byteBuffer.size - keep)
  }

  private fun hasHeader(packetBytes: ByteArray, header: ByteString): Boolean {
    return packetBytes[0] == header[0] && packetBytes[1] == header[1] && packetBytes[2] == header[2]
  }

  companion object {
    private const val PACKET_SIZE = 512
    private const val HEADER_SIZE = 3
    private val MUON_HEAD: ByteString =
      byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()).toByteString()
    private val TIMELINE_HEAD: ByteString = byteArrayOf(0x12, 0x34, 0x56).toByteString()
  }
}
