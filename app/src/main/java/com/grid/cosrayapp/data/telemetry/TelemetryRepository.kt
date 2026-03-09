package com.grid.cosrayapp.data.telemetry

import android.util.Log
import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.Protocol
import com.grid.cosrayapp.domain.model.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TelemetryRepository(
        private val api: CosRayApi,
        private val bleRepository: BleRepository,
        private val authRepository: AuthRepository,
        externalScope: CoroutineScope,
) {
  private val packetAssembler = FirmwarePacketAssembler()
  private var lastRequestedDeviceMac: String? = null

  private val _buffer = MutableStateFlow<List<TelemetrySample>>(emptyList())
  val bufferedSamples: StateFlow<List<TelemetrySample>> = _buffer.asStateFlow()

  private val _uploadBuffer = MutableStateFlow<List<PacketUploadRequest>>(emptyList())

  private val _liveTelemetry = MutableStateFlow<List<TelemetrySample>>(emptyList())
  val liveTelemetry: StateFlow<List<TelemetrySample>> = _liveTelemetry.asStateFlow()

  private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
  val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

  init {
    externalScope.launch {
      bleRepository.connectionState.collect { state: BleConnectionState ->
        when (state) {
          is BleConnectionState.Connected -> {
            _connectedDevice.value = state.device
            requestFirmwarePacketsIfNeeded(state.device.macAddress)
          }
          else -> {
            _connectedDevice.value = null
            lastRequestedDeviceMac = null
          }
        }
      }
    }

    externalScope.launch {
      bleRepository.rawPackets.collect { rawPacket ->
        val deviceMac = _connectedDevice.value?.macAddress ?: return@collect
        val packets = packetAssembler.consume(rawPacket.data, deviceMac)
        if (packets.isNotEmpty()) {
          Log.d(
                  TAG,
                  "Assembled ${packets.size} firmware packet(s) from ${rawPacket.data.size}-byte BLE notification"
          )
          appendUploadRequests(packets.map(ParsedFirmwarePacket::uploadRequest))
          appendSamples(packets.flatMap(ParsedFirmwarePacket::samples))
        }
      }
    }
  }

  suspend fun requestFirmwarePackets(): CosRayResult<Unit> {
    val deviceMac =
            _connectedDevice.value?.macAddress
                    ?: return CosRayResult.Error(IllegalStateException("No connected device"))
    return sendInitialCommands(deviceMac)
  }

  suspend fun sendStatusCommand(): CosRayResult<Unit> =
          sendSingleCommand(Protocol.Command.buildStatusCommand())

  suspend fun sendMuonStartCommand(): CosRayResult<Unit> =
          sendSingleCommand(
                  Protocol.Command.buildStartCommand(packetType = Protocol.Command.TYPE_MUON)
          )

  suspend fun sendTimelineStartCommand(): CosRayResult<Unit> =
          sendSingleCommand(
                  Protocol.Command.buildStartCommand(packetType = Protocol.Command.TYPE_TIMELINE)
          )

  suspend fun sendStopCommand(): CosRayResult<Unit> =
          sendSingleCommand(Protocol.Command.buildStopCommand())

  private suspend fun requestFirmwarePacketsIfNeeded(deviceMac: String) {
    if (lastRequestedDeviceMac == deviceMac) return
    when (val result = sendInitialCommands(deviceMac)) {
      is CosRayResult.Success -> lastRequestedDeviceMac = deviceMac
      is CosRayResult.Error -> {
        Log.w(TAG, "Failed to send initial firmware commands for $deviceMac", result.throwable)
      }
    }
  }

  private suspend fun sendInitialCommands(deviceMac: String): CosRayResult<Unit> {
    val commands =
            listOf(
                    Protocol.Command.buildStatusCommand(),
                    Protocol.Command.buildStartCommand(packetType = Protocol.Command.TYPE_MUON),
                    Protocol.Command.buildStartCommand(packetType = Protocol.Command.TYPE_TIMELINE),
            )

    commands.forEachIndexed { index, command ->
      when (val result = bleRepository.sendCommand(command)) {
        is CosRayResult.Success -> {
          Log.i(TAG, "Sent firmware command ${index + 1}/${commands.size} to $deviceMac")
        }
        is CosRayResult.Error -> return result
      }
    }

    return CosRayResult.Success(Unit)
  }

  private suspend fun sendSingleCommand(command: ByteArray): CosRayResult<Unit> {
    val deviceMac =
            _connectedDevice.value?.macAddress
                    ?: return CosRayResult.Error(IllegalStateException("No connected device"))
    return when (val result = bleRepository.sendCommand(command)) {
      is CosRayResult.Success -> {
        Log.i(TAG, "Sent manual firmware command to $deviceMac")
        CosRayResult.Success(Unit)
      }
      is CosRayResult.Error -> result
    }
  }

  private fun appendUploadRequests(requests: List<PacketUploadRequest>) {
    if (requests.isEmpty()) return
    _uploadBuffer.update { list -> (list + requests).takeLast(BUFFER_SIZE) }
  }

  private fun appendSamples(samples: List<TelemetrySample>) {
    if (samples.isEmpty()) return
    _buffer.update { list -> (list + samples).takeLast(BUFFER_SIZE) }
    _liveTelemetry.update { list ->
      (list + samples).sortedByDescending(TelemetrySample::recordedAt).take(MAX_LIVE_SAMPLES)
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
    _liveTelemetry.value = emptyList()
    _uploadBuffer.value = emptyList()
    packetAssembler.reset()
  }

  companion object {
    private const val TAG = "TelemetryRepository"
    private const val BUFFER_SIZE = 128
    private const val MAX_LIVE_SAMPLES = 30
  }
}

internal class FirmwarePacketAssembler {
  private val partialPackets = linkedMapOf<Int, PartialPacket>()

  fun consume(chunk: ByteArray, macAddress: String): List<ParsedFirmwarePacket> {
    if (chunk.size <= BLE_HEADER_SIZE) return emptyList()

    val globalTotal = chunk[0].toUnsignedInt()
    val globalIndex = chunk[1].toUnsignedInt()
    val localTotal = chunk[2].toUnsignedInt()
    val localIndex = chunk[3].toUnsignedInt()
    if (globalTotal == 0 || globalIndex == 0 || localTotal == 0 || localIndex !in 1..localTotal) {
      return emptyList()
    }

    val payload = chunk.copyOfRange(BLE_HEADER_SIZE, chunk.size)
    if (payload.isEmpty()) return emptyList()

    var partialPacket = partialPackets[globalIndex]
    if (partialPacket == null ||
                    partialPacket.globalTotal != globalTotal ||
                    partialPacket.localTotal != localTotal ||
                    partialPacket.payloadSize != payload.size ||
                    (localIndex == 1 && partialPacket.fragments.isNotEmpty())
    ) {
      partialPacket = PartialPacket(globalTotal, localTotal, payload.size)
      partialPackets[globalIndex] = partialPacket
    }

    partialPacket.fragments[localIndex] = payload
    trimActivePackets()

    if (partialPacket.fragments.size < localTotal) return emptyList()

    val packetBytes = ByteArray(localTotal * partialPacket.payloadSize)
    for (index in 1..localTotal) {
      val fragment = partialPacket.fragments[index] ?: return emptyList()
      fragment.copyInto(
              destination = packetBytes,
              destinationOffset = (index - 1) * partialPacket.payloadSize,
      )
    }

    partialPackets.remove(globalIndex)
    if (packetBytes.size < COMPLETE_PACKET_SIZE) return emptyList()

    return FirmwarePacketMapper.parse(macAddress, packetBytes.copyOf(COMPLETE_PACKET_SIZE))
            ?.let(::listOf)
            ?: emptyList()
  }

  fun reset() {
    partialPackets.clear()
  }

  private fun trimActivePackets() {
    while (partialPackets.size > MAX_ACTIVE_PACKETS) {
      val oldestKey = partialPackets.entries.firstOrNull()?.key ?: return
      partialPackets.remove(oldestKey)
    }
  }

  private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

  private data class PartialPacket(
          val globalTotal: Int,
          val localTotal: Int,
          val payloadSize: Int,
          val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
  )

  companion object {
    private const val BLE_HEADER_SIZE = 4
    private const val COMPLETE_PACKET_SIZE = 512
    private const val MAX_ACTIVE_PACKETS = 8
  }
}
