package com.grid.cosrayapp.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.data.telemetry.TelemetryRepository
import com.grid.cosrayapp.domain.model.SignalQuality
import com.grid.cosrayapp.domain.model.TelemetrySample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceViewModel(
  private val bleRepository: BleRepository,
  private val telemetryRepository: TelemetryRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DeviceUiState())
  val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(
          bleRepository.scanResults,
          bleRepository.isScanning,
          bleRepository.connectionState,
          telemetryRepository.bufferedSamples,
        ) { devices, isScanning, connectionState, samples ->
          val items =
            devices.map { device ->
              DeviceItem(
                detectorId = device.id.value,
                name = device.name,
                macAddress = device.macAddress,
                rssi = device.signal.rssi,
                signalQuality = device.signal.quality,
              )
            }
          DeviceUiState(
            devices = items,
            isScanning = isScanning,
            connectionState = connectionState,
            latestTelemetry = samples.lastOrNull(),
            hasPermissions = bleRepository.hasPermissions(),
          )
        }
        .collect { state -> _uiState.value = state }
    }
  }

  fun onPermissionsChanged(granted: Boolean) {
    _uiState.update { it.copy(hasPermissions = granted) }
  }

  fun startScan() {
    viewModelScope.launch { bleRepository.startScan() }
  }

  fun stopScan() {
    bleRepository.stopScan()
  }

  fun connect(address: String) {
    viewModelScope.launch { bleRepository.connect(address) }
  }

  fun disconnect() {
    bleRepository.disconnect()
  }
}

data class DeviceUiState(
  val devices: List<DeviceItem> = emptyList(),
  val isScanning: Boolean = false,
  val connectionState: BleConnectionState = BleConnectionState.Disconnected,
  val hasPermissions: Boolean = false,
  val latestTelemetry: TelemetrySample? = null,
)

data class DeviceItem(
  val detectorId: String,
  val name: String?,
  val macAddress: String,
  val rssi: Int,
  val signalQuality: SignalQuality,
)
