package com.grid.cosrayapp.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.ui.UiMessage
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.data.telemetry.TelemetryRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import com.grid.cosrayapp.core.ble.RawPacket
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel
@Inject
constructor(
  private val telemetryRepository: TelemetryRepository,
  private val authRepository: AuthRepository,
  private val bleRepository: com.grid.cosrayapp.data.ble.BleRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DashboardUiState())
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(
          telemetryRepository.liveTelemetry,
          telemetryRepository.connectedDevice,
          authRepository.authState,
        ) { samples, device, authState ->
          val allSamples = samples.take(MAX_SAMPLES)
          val muonSamples = allSamples.filter { it.packetMetadata?.packetType == PacketType.MUON }
          val timelineSamples =
            allSamples.filter { it.packetMetadata?.packetType == PacketType.TIMELINE }

          // Extract latest location, orientation, and SiPM data from timeline events
          val latestTimeline = timelineSamples.firstOrNull()

          // Calculate packet statistics
          val stats = calculatePacketStatistics(allSamples)

          DashboardUiState(
            user = (authState as? AuthState.Authenticated)?.user,
            device = device,
            samples = allSamples,
            muonEvents = muonSamples.take(MAX_MUON_EVENTS),
            timelineEvents = timelineSamples.take(MAX_TIMELINE_EVENTS),
            rawPackets = emptyList(), // We will update this via a separate flow
            deviceLocation = latestTimeline?.location,
            deviceOrientation = latestTimeline?.acceleration,
            sipmStatus = latestTimeline?.sipmMonitoring,
            packetStats = stats,
            isUploading = _uiState.value.isUploading,
            uploadMessage = _uiState.value.uploadMessage,
          )
        }
        .collect { state -> _uiState.value = state.copy(rawPackets = _uiState.value.rawPackets) }
    }
    
    viewModelScope.launch {
      bleRepository.rawPackets.collect { rawPacket ->
        _uiState.update { state ->
          val newRawPackets = (listOf(rawPacket) + state.rawPackets).take(MAX_RAW_PACKETS)
          state.copy(rawPackets = newRawPackets)
        }
      }
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
              uploadMessage =
                result.throwable.message?.let(UiMessage::fromRaw)
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

  companion object {
    private const val MAX_SAMPLES = 30
    private const val MAX_MUON_EVENTS = 50
    private const val MAX_TIMELINE_EVENTS = 20
    private const val MAX_RAW_PACKETS = 50
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
  val uploadMessage: UiMessage? = null,
)

data class PacketStatistics(
  val muonPacketCount: Int = 0,
  val timelinePacketCount: Int = 0,
  val totalEventCount: Int = 0,
  val lastPacketTime: Instant? = null,
  val averageEnergyAdcCounts: Double? = null,
)
