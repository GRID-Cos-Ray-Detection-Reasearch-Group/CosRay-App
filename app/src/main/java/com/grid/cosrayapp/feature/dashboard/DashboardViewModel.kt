package com.grid.cosrayapp.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ble.RawPacket
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.ui.UiMessage
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.data.telemetry.TelemetryRepository
import com.grid.cosrayapp.data.telemetry.db.RawPacketDao
import com.grid.cosrayapp.data.telemetry.db.TelemetrySampleDao
import com.grid.cosrayapp.data.telemetry.db.toDomain
import com.grid.cosrayapp.domain.model.AccelerationSnapshot
import com.grid.cosrayapp.domain.model.BleDevice
import com.grid.cosrayapp.domain.model.LocationSnapshot
import com.grid.cosrayapp.domain.model.PacketType
import com.grid.cosrayapp.domain.model.SipmMonitoring
import com.grid.cosrayapp.domain.model.TelemetrySample
import com.grid.cosrayapp.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel
@Inject
constructor(
        private val telemetryRepository: TelemetryRepository,
        private val authRepository: AuthRepository,
        private val telemetrySampleDao: TelemetrySampleDao,
        private val rawPacketDao: RawPacketDao,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DashboardUiState())
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val detectorIdFlow = telemetryRepository.connectedDevice.map { it?.macAddress }

      val samplesFlow =
        detectorIdFlow.flatMapLatest { detectorId ->
          if (detectorId == null) {
            flowOf(emptyList<TelemetrySample>())
          } else {
            telemetrySampleDao.observeLatest(detectorId = detectorId, limit = MAX_SAMPLES)
              .map { list -> list.map { it.toDomain() } }
          }
        }

      val muonFlow =
        detectorIdFlow.flatMapLatest { detectorId ->
          if (detectorId == null) {
            flowOf(emptyList<TelemetrySample>())
          } else {
            telemetrySampleDao.observeLatestByPacketType(
              detectorId = detectorId,
              packetType = PacketType.MUON,
              limit = MAX_MUON_EVENTS,
            ).map { list -> list.map { it.toDomain() } }
          }
        }

      val timelineFlow =
        detectorIdFlow.flatMapLatest { detectorId ->
          if (detectorId == null) {
            flowOf(emptyList<TelemetrySample>())
          } else {
            telemetrySampleDao.observeLatestByPacketType(
              detectorId = detectorId,
              packetType = PacketType.TIMELINE,
              limit = MAX_TIMELINE_EVENTS,
            ).map { list -> list.map { it.toDomain() } }
          }
        }

      val rawPacketsFlow =
        detectorIdFlow.flatMapLatest { detectorId ->
          if (detectorId == null) {
            flowOf(emptyList<RawPacket>())
          } else {
            rawPacketDao.observeLatest(detectorId = detectorId, limit = MAX_RAW_PACKETS)
              .map { entities ->
                entities.map {
                  RawPacket(
                    id = it.id,
                    characteristicId = java.util.UUID.fromString(it.characteristicId),
                    data = it.data,
                    timestamp = it.receivedAtEpochMillis,
                  )
                }
              }
          }
        }

      data class DbBackedDashboardSnapshot(
        val samples: List<TelemetrySample>,
        val muonEvents: List<TelemetrySample>,
        val timelineEvents: List<TelemetrySample>,
        val rawPackets: List<RawPacket>,
        val device: BleDevice?,
      )

      val snapshotFlow =
        combine(
          samplesFlow,
          muonFlow,
          timelineFlow,
          rawPacketsFlow,
          telemetryRepository.connectedDevice,
        ) { samples, muonEvents, timelineEvents, rawPackets, device ->
          DbBackedDashboardSnapshot(
            samples = samples,
            muonEvents = muonEvents,
            timelineEvents = timelineEvents,
            rawPackets = rawPackets,
            device = device,
          )
        }

      combine(snapshotFlow, authRepository.authState) { snapshot, authState ->
        val latestTimeline = snapshot.timelineEvents.firstOrNull()
        val stats = calculatePacketStatistics(snapshot.samples)

        DashboardUiState(
          user = (authState as? AuthState.Authenticated)?.user,
          device = snapshot.device,
          samples = snapshot.samples,
          muonEvents = snapshot.muonEvents,
          timelineEvents = snapshot.timelineEvents,
          rawPackets = snapshot.rawPackets,
          deviceLocation = latestTimeline?.location,
          deviceOrientation = latestTimeline?.acceleration,
          sipmStatus = latestTimeline?.sipmMonitoring,
          packetStats = stats,
          isUploading = _uiState.value.isUploading,
          isSendingCommand = _uiState.value.isSendingCommand,
          uploadMessage = _uiState.value.uploadMessage,
        )
      }.collect { state -> _uiState.value = state }
    }
  }

  private fun calculatePacketStatistics(samples: List<TelemetrySample>): PacketStatistics {
    var muonCount = 0
    var timelineCount = 0
    var energySum = 0.0
    var muonEnergyCount = 0

    samples.forEach { sample ->
      when (sample.packetMetadata?.packetType) {
        PacketType.MUON -> {
          muonCount++
          energySum += sample.acquisition.particleCount.toDouble()
          muonEnergyCount++
        }
        PacketType.TIMELINE -> timelineCount++
        null -> {} // Skip samples without packet metadata
      }
    }

    val avgEnergy = if (muonEnergyCount > 0) energySum / muonEnergyCount else null
    val lastPacket = samples.maxByOrNull { it.recordedAt }?.recordedAt

    return PacketStatistics(
            muonPacketCount = muonCount,
            timelinePacketCount = timelineCount,
            totalEventCount = samples.size,
            lastPacketTime = lastPacket,
            averageEnergyAdcCounts = avgEnergy,
    )
  }

  fun uploadBufferedTelemetry() {
    viewModelScope.launch {
      _uiState.update { it.copy(isUploading = true, uploadMessage = null) }
      when (val result = telemetryRepository.uploadBufferedSamples()) {
        is CosRayResult.Success -> {
          _uiState.update {
            it.copy(
                    isUploading = false,
                    uploadMessage = UiMessage.from(R.string.dashboard_upload_success),
            )
          }
        }
        is CosRayResult.Error -> {
          _uiState.update {
            it.copy(
                    isUploading = false,
                    uploadMessage = result.throwable.message?.let(UiMessage::fromRaw)
                                    ?: UiMessage.from(R.string.dashboard_upload_failed),
            )
          }
        }
      }
    }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(uploadMessage = null) }
  }

  fun sendStatusCommand() {
    sendFirmwareCommand(
            successMessage = UiMessage.from(R.string.dashboard_command_status_success),
            command = telemetryRepository::sendStatusCommand,
    )
  }

  fun sendMuonStartCommand() {
    sendFirmwareCommand(
            successMessage = UiMessage.from(R.string.dashboard_command_muon_success),
            command = telemetryRepository::sendMuonStartCommand,
    )
  }

  fun sendTimelineStartCommand() {
    sendFirmwareCommand(
            successMessage = UiMessage.from(R.string.dashboard_command_timeline_success),
            command = telemetryRepository::sendTimelineStartCommand,
    )
  }

  fun sendStopCommand() {
    sendFirmwareCommand(
            successMessage = UiMessage.from(R.string.dashboard_command_stop_success),
            command = telemetryRepository::sendStopCommand,
    )
  }

  private fun sendFirmwareCommand(
          successMessage: UiMessage,
          command: suspend () -> CosRayResult<Unit>,
  ) {
    viewModelScope.launch {
      _uiState.update { it.copy(isSendingCommand = true, uploadMessage = null) }
      when (val result = command()) {
        is CosRayResult.Success -> {
          _uiState.update {
            it.copy(
                    isSendingCommand = false,
                    uploadMessage = successMessage,
            )
          }
        }
        is CosRayResult.Error -> {
          _uiState.update {
            it.copy(
                    isSendingCommand = false,
                    uploadMessage = result.throwable.message?.let(UiMessage::fromRaw)
                                    ?: UiMessage.from(R.string.dashboard_command_failed),
            )
          }
        }
      }
    }
  }

  companion object {
    private const val MAX_SAMPLES = 500
    private const val MAX_MUON_EVENTS = 200
    private const val MAX_TIMELINE_EVENTS = 200
    private const val MAX_RAW_PACKETS = 200
  }
}

data class DashboardUiState(
        val user: User? = null,
        val device: BleDevice? = null,
        val samples: List<TelemetrySample> = emptyList(),
        val muonEvents: List<TelemetrySample> = emptyList(),
        val timelineEvents: List<TelemetrySample> = emptyList(),
        val rawPackets: List<RawPacket> = emptyList(),
        val deviceLocation: LocationSnapshot? = null,
        val deviceOrientation: AccelerationSnapshot? = null,
        val sipmStatus: SipmMonitoring? = null,
        val packetStats: PacketStatistics = PacketStatistics(),
        val isUploading: Boolean = false,
        val isSendingCommand: Boolean = false,
        val uploadMessage: UiMessage? = null,
)

data class PacketStatistics(
        val muonPacketCount: Int = 0,
        val timelinePacketCount: Int = 0,
        val totalEventCount: Int = 0,
        val lastPacketTime: Instant? = null,
        val averageEnergyAdcCounts: Double? = null,
)
