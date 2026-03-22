package com.grid.cosrayapp.data.telemetry

import android.util.Log
import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.data.telemetry.db.RawPacketDao
import com.grid.cosrayapp.data.telemetry.db.RawPacketEntity
import com.grid.cosrayapp.data.telemetry.db.TelemetrySampleDao
import com.grid.cosrayapp.data.telemetry.db.toEntity
import com.grid.cosrayapp.data.telemetry.upload.UploadQueue
import com.grid.cosrayapp.data.telemetry.upload.UploadQueueItem
import com.grid.cosrayapp.data.telemetry.upload.UploadQueueStats
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
        private val uploadQueue: UploadQueue,
        private val telemetrySampleDao: TelemetrySampleDao,
        private val rawPacketDao: RawPacketDao,
        externalScope: CoroutineScope,
) {
  private val externalScope: CoroutineScope = externalScope
  private val packetAssembler = FirmwarePacketAssembler(logger = AndroidFirmwarePacketAssemblerLogger)
  private var lastRequestedDeviceMac: String? = null

  private val _buffer = MutableStateFlow<List<TelemetrySample>>(emptyList())
  val bufferedSamples: StateFlow<List<TelemetrySample>> = _buffer.asStateFlow()

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
        persistRawPacket(deviceMac = deviceMac, rawPacket = rawPacket)
        val packets = packetAssembler.consume(rawPacket.data, deviceMac)
        if (packets.isNotEmpty()) {
          Log.d(
                  TAG,
                  "Assembled ${packets.size} firmware packet(s) from ${rawPacket.data.size}-byte BLE notification"
          )
          uploadQueue.enqueue(packets.map(ParsedFirmwarePacket::uploadRequest))
          val samples = packets.flatMap(ParsedFirmwarePacket::samples)
          persistSamples(samples)
          appendSamples(samples)
        }
      }
    }
  }

  private fun persistRawPacket(deviceMac: String, rawPacket: com.grid.cosrayapp.core.ble.RawPacket) {
    externalScope.launch {
      runCatching {
        rawPacketDao.insert(
          RawPacketEntity(
            detectorId = deviceMac,
            characteristicId = rawPacket.characteristicId.toString(),
            receivedAtEpochMillis = rawPacket.timestamp,
            data = rawPacket.data,
            isUploaded = false,
          )
        )
      }
        .onFailure { e -> Log.w(TAG, "Failed to persist raw packet", e) }
    }
  }

  private fun persistSamples(samples: List<TelemetrySample>) {
    if (samples.isEmpty()) return
    externalScope.launch {
      runCatching {
        telemetrySampleDao.upsertAll(samples.map { it.toEntity().copy(isUploaded = false) })
      }
        .onFailure { e -> Log.w(TAG, "Failed to persist telemetry samples", e) }
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

  private fun appendSamples(samples: List<TelemetrySample>) {
    if (samples.isEmpty()) return
    _buffer.update { list -> list + samples }
    _liveTelemetry.update { list ->
      (list + samples).sortedByDescending(TelemetrySample::recordedAt)
    }
  }

  suspend fun uploadBufferedSamples(): CosRayResult<Unit> {
    val uploadStartTime = System.currentTimeMillis()
    val uploadResult: CosRayResult<Unit> =
            runCosRayCatching {
              while (true) {
                val batch = uploadQueue.peekBatch(limit = UPLOAD_BATCH_SIZE)
                if (batch.isEmpty()) break

                val tokenResult = authRepository.ensureValidToken()
                val accessToken =
                        when (tokenResult) {
                          is CosRayResult.Success -> tokenResult.data
                          is CosRayResult.Error -> throw tokenResult.throwable
                        }
                val uploadedIds = uploadBatch(accessToken = accessToken, batch = batch)
                if (uploadedIds.isNotEmpty()) uploadQueue.delete(uploadedIds)
              }
            }

    return if (uploadResult is CosRayResult.Success) {
      externalScope.launch {
        runCatching {
          telemetrySampleDao.markAsUploaded(maxTimestamp = uploadStartTime)
          rawPacketDao.markAsUploaded(maxTimestamp = uploadStartTime)
          telemetrySampleDao.pruneUploaded(keepLatest = MAX_DB_SAMPLES_PER_DETECTOR)
          rawPacketDao.pruneUploaded(keepLatest = MAX_DB_RAW_PACKETS_PER_DETECTOR)
        }
      }
      CosRayResult.Success(Unit)
    } else {
      uploadResult
    }
  }

  private suspend fun uploadBatch(
    accessToken: String,
    batch: List<UploadQueueItem>,
  ): List<Long> {
    if (batch.isEmpty()) return emptyList()

    val uploadedIds = mutableListOf<Long>()
    val failure =
      runCatching {
        batch.forEach { item ->
          api.uploadPacket(accessToken, item.request)
          uploadedIds += item.id
        }
      }
        .exceptionOrNull()

    if (failure != null && uploadedIds.isNotEmpty()) {
      runCatching { uploadQueue.delete(uploadedIds) }
        .onFailure { deleteError -> failure.addSuppressed(deleteError) }
    }

    if (failure != null) throw failure
    return uploadedIds
  }

  fun clearBuffer() {
    _buffer.value = emptyList()
    _liveTelemetry.value = emptyList()
    packetAssembler.reset()
    externalScope.launch { uploadQueue.clear() }
  }

  fun firmwarePacketAssemblerStats(): FirmwarePacketAssemblerStats = packetAssembler.snapshotStats()

  suspend fun uploadQueueStats(): UploadQueueStats = uploadQueue.stats()

  companion object {
    private const val TAG = "TelemetryRepository"
    private const val BUFFER_SIZE = 128
    private const val MAX_LIVE_SAMPLES = 30
    private const val UPLOAD_BATCH_SIZE = 32
    private const val MAX_DB_SAMPLES_PER_DETECTOR = 10_000
    private const val MAX_DB_RAW_PACKETS_PER_DETECTOR = 2_000
  }
}

class FirmwarePacketAssembler(
        private val nowMillis: () -> Long = { System.currentTimeMillis() },
        private val packetTtlMillis: Long = DEFAULT_PACKET_TTL_MILLIS,
        private val logger: FirmwarePacketAssemblerLogger = NoopFirmwarePacketAssemblerLogger,
) {
  private val partialPackets = linkedMapOf<Int, PartialPacket>()

  private val stats = Stats()

  fun consume(chunk: ByteArray, macAddress: String): List<ParsedFirmwarePacket> {
    cleanupExpiredPackets(nowMillis = nowMillis(), macAddress = macAddress)

    if (chunk.size <= BLE_HEADER_SIZE) {
      stats.droppedFragments++
      logDrop(
              reason = DropReason.CHUNK_TOO_SHORT,
              macAddress = macAddress,
              globalIndex = null,
              globalTotal = null,
              localIndex = null,
              localTotal = null,
              payloadSize = null,
      )
      return emptyList()
    }

    val globalTotal = chunk[0].toUnsignedInt()
    val globalIndex = chunk[1].toUnsignedInt()
    val localTotal = chunk[2].toUnsignedInt()
    val localIndex = chunk[3].toUnsignedInt()
    if (globalTotal == 0 || globalIndex == 0 || localTotal == 0 || localIndex !in 1..localTotal) {
      stats.droppedFragments++
      logDrop(
              reason = DropReason.INVALID_HEADER,
              macAddress = macAddress,
              globalIndex = globalIndex,
              globalTotal = globalTotal,
              localIndex = localIndex,
              localTotal = localTotal,
              payloadSize = (chunk.size - BLE_HEADER_SIZE).coerceAtLeast(0),
      )
      return emptyList()
    }

    val payload = chunk.copyOfRange(BLE_HEADER_SIZE, chunk.size)
    if (payload.isEmpty()) {
      stats.droppedFragments++
      logDrop(
              reason = DropReason.EMPTY_PAYLOAD,
              macAddress = macAddress,
              globalIndex = globalIndex,
              globalTotal = globalTotal,
              localIndex = localIndex,
              localTotal = localTotal,
              payloadSize = 0,
      )
      return emptyList()
    }

    var partialPacket = partialPackets[globalIndex]
    if (partialPacket == null ||
                    partialPacket.globalTotal != globalTotal ||
                    partialPacket.localTotal != localTotal ||
                    partialPacket.payloadSize != payload.size
    ) {
      if (partialPacket != null) {
        stats.droppedPackets++
        logDrop(
                reason = DropReason.PARTIAL_RESET,
                macAddress = macAddress,
                globalIndex = globalIndex,
                globalTotal = globalTotal,
                localIndex = localIndex,
                localTotal = localTotal,
                payloadSize = payload.size,
        )
      }
      partialPacket = PartialPacket(globalTotal, localTotal, payload.size)
      partialPacket.lastUpdatedAtMillis = nowMillis()
      partialPackets[globalIndex] = partialPacket
    }

    val existing = partialPacket.fragments.put(localIndex, payload)
    if (existing != null) {
      stats.duplicateFragments++
      logDrop(
              reason = DropReason.DUPLICATE_FRAGMENT,
              macAddress = macAddress,
              globalIndex = globalIndex,
              globalTotal = globalTotal,
              localIndex = localIndex,
              localTotal = localTotal,
              payloadSize = payload.size,
      )
    }

    if (partialPacket.maxSeenIndex > 0 && localIndex < partialPacket.maxSeenIndex) {
      stats.outOfOrderFragments++
    }
    partialPacket.maxSeenIndex = maxOf(partialPacket.maxSeenIndex, localIndex)
    partialPacket.lastUpdatedAtMillis = nowMillis()

    trimActivePackets(macAddress)

    if (partialPacket.fragments.size < localTotal) {
      return emptyList()
    }

    val packetBytes = ByteArray(localTotal * partialPacket.payloadSize)
    for (index in 1..localTotal) {
      val fragment = partialPacket.fragments[index]
      if (fragment == null) {
        stats.droppedPackets++
        logDrop(
                reason = DropReason.MISSING_FRAGMENT_ON_ASSEMBLE,
                macAddress = macAddress,
                globalIndex = globalIndex,
                globalTotal = globalTotal,
                localIndex = null,
                localTotal = localTotal,
                payloadSize = partialPacket.payloadSize,
        )
        return emptyList()
      }
      fragment.copyInto(
              destination = packetBytes,
              destinationOffset = (index - 1) * partialPacket.payloadSize,
      )
    }

    partialPackets.remove(globalIndex)
    if (packetBytes.size < COMPLETE_PACKET_SIZE) {
      stats.droppedPackets++
      logDrop(
              reason = DropReason.PACKET_TOO_SHORT,
              macAddress = macAddress,
              globalIndex = globalIndex,
              globalTotal = globalTotal,
              localIndex = null,
              localTotal = localTotal,
              payloadSize = partialPacket.payloadSize,
      )
      return emptyList()
    }

    val parsed = FirmwarePacketMapper.parse(macAddress, packetBytes.copyOf(COMPLETE_PACKET_SIZE))
    return if (parsed != null) {
      stats.assembledPackets++
      maybeLogSummary(macAddress)
      listOf(parsed)
    } else {
      stats.parseFailures++
      stats.droppedPackets++
      logDrop(
              reason = DropReason.PARSE_FAILED,
              macAddress = macAddress,
              globalIndex = globalIndex,
              globalTotal = globalTotal,
              localIndex = null,
              localTotal = localTotal,
              payloadSize = partialPacket.payloadSize,
      )
      emptyList()
    }
  }

  fun reset() {
    partialPackets.clear()
  }

  fun snapshotStats(): FirmwarePacketAssemblerStats =
          FirmwarePacketAssemblerStats(
                  assembledPackets = stats.assembledPackets,
                  droppedFragments = stats.droppedFragments,
                  droppedPackets = stats.droppedPackets,
                  parseFailures = stats.parseFailures,
                  duplicateFragments = stats.duplicateFragments,
                  outOfOrderFragments = stats.outOfOrderFragments,
                  activePackets = partialPackets.size,
          )

  private fun maybeLogSummary(macAddress: String) {
    val total = stats.assembledPackets + stats.droppedPackets + stats.droppedFragments
    if (total == 0L) return
    if (total % SUMMARY_LOG_EVERY != 0L) return
    val s = snapshotStats()
    logger.info(
            TAG,
            "summary mac=$macAddress assembled=${s.assembledPackets} droppedFragments=${s.droppedFragments} droppedPackets=${s.droppedPackets} parseFailures=${s.parseFailures} duplicates=${s.duplicateFragments} outOfOrder=${s.outOfOrderFragments} active=${s.activePackets}"
    )
  }

  private fun trimActivePackets(macAddress: String) {
    while (partialPackets.size > MAX_ACTIVE_PACKETS) {
      val oldestKey = partialPackets.entries.firstOrNull()?.key ?: return
      partialPackets.remove(oldestKey)
      stats.droppedPackets++
      logDrop(
              reason = DropReason.TRIMMED_OLD_PARTIAL,
              macAddress = macAddress,
              globalIndex = oldestKey,
              globalTotal = null,
              localIndex = null,
              localTotal = null,
              payloadSize = null,
      )
    }
  }

  private fun cleanupExpiredPackets(nowMillis: Long, macAddress: String) {
    val expiredKeys =
            partialPackets
                    .filterValues { partial -> nowMillis - partial.lastUpdatedAtMillis > packetTtlMillis }
                    .keys
    if (expiredKeys.isEmpty()) return

    expiredKeys.forEach { key ->
      partialPackets.remove(key)
      stats.droppedPackets++
      logDrop(
              reason = DropReason.EXPIRED_PARTIAL_TTL,
              macAddress = macAddress,
              globalIndex = key,
              globalTotal = null,
              localIndex = null,
              localTotal = null,
              payloadSize = null,
      )
    }
  }

  private fun logDrop(
          reason: DropReason,
          macAddress: String?,
          globalIndex: Int?,
          globalTotal: Int?,
          localIndex: Int?,
          localTotal: Int?,
          payloadSize: Int?,
  ) {
    logger.debug(
            TAG,
            "consume drop reason=${reason.code} mac=${macAddress ?: "?"} globalIndex=${globalIndex ?: "?"} globalTotal=${globalTotal ?: "?"} localIndex=${localIndex ?: "?"} localTotal=${localTotal ?: "?"} payloadSize=${payloadSize ?: "?"}"
    )
  }

  private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

  private data class PartialPacket(
          val globalTotal: Int,
          val localTotal: Int,
          val payloadSize: Int,
          val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
          var lastUpdatedAtMillis: Long = 0L,
          var maxSeenIndex: Int = 0,
  )

  companion object {
    private const val TAG = "FirmwarePacketAsm"
    private const val BLE_HEADER_SIZE = 4
    private const val COMPLETE_PACKET_SIZE = 512
    private const val MAX_ACTIVE_PACKETS = 8
    private const val DEFAULT_PACKET_TTL_MILLIS = 5_000L
    private const val SUMMARY_LOG_EVERY = 200L
  }
}

interface FirmwarePacketAssemblerLogger {
  fun debug(tag: String, message: String)

  fun info(tag: String, message: String)
}

object NoopFirmwarePacketAssemblerLogger : FirmwarePacketAssemblerLogger {
  override fun debug(tag: String, message: String) = Unit

  override fun info(tag: String, message: String) = Unit
}

object AndroidFirmwarePacketAssemblerLogger : FirmwarePacketAssemblerLogger {
  override fun debug(tag: String, message: String) {
    Log.d(tag, message)
  }

  override fun info(tag: String, message: String) {
    Log.i(tag, message)
  }
}

data class FirmwarePacketAssemblerStats(
        val assembledPackets: Long,
        val droppedFragments: Long,
        val droppedPackets: Long,
        val parseFailures: Long,
        val duplicateFragments: Long,
        val outOfOrderFragments: Long,
        val activePackets: Int,
)

private enum class DropReason(val code: String) {
  CHUNK_TOO_SHORT("chunk_too_short"),
  INVALID_HEADER("invalid_header"),
  EMPTY_PAYLOAD("empty_payload"),
  PARTIAL_RESET("partial_reset"),
  DUPLICATE_FRAGMENT("duplicate_fragment"),
  MISSING_FRAGMENT_ON_ASSEMBLE("missing_fragment_on_assemble"),
  PACKET_TOO_SHORT("packet_too_short"),
  PARSE_FAILED("parse_failed"),
  TRIMMED_OLD_PARTIAL("trimmed_old_partial"),
  EXPIRED_PARTIAL_TTL("expired_partial_ttl"),
}

private class Stats(
        var assembledPackets: Long = 0,
        var droppedFragments: Long = 0,
        var droppedPackets: Long = 0,
        var parseFailures: Long = 0,
        var duplicateFragments: Long = 0,
        var outOfOrderFragments: Long = 0,
)
