package com.grid.cosrayapp.feature.detectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ble.BleConnectionState
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.ui.UiMessage
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.data.device.DetectorManagementRepository
import com.grid.cosrayapp.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DetectorManagementViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val detectorManagementRepository: DetectorManagementRepository,
  private val bleRepository: BleRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DetectorManagementUiState())
  val uiState: StateFlow<DetectorManagementUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.authState.collect { authState ->
        when (authState) {
          is AuthState.Authenticated -> {
            _uiState.update {
              it.copy(
                isAuthenticated = true,
                user = authState.user,
                isLoading = true,
                statusMessage = null,
              )
            }
            refreshDevices()
          }
          AuthState.Loading -> _uiState.update { it.copy(isLoading = true) }
          AuthState.Unauthenticated -> _uiState.value = DetectorManagementUiState()
        }
      }
    }

    viewModelScope.launch {
      bleRepository.connectionState.collect { state ->
        if (state is BleConnectionState.Connected) {
          _uiState.update {
            it.copy(
              connectedDeviceMac = state.device.macAddress,
              connectedDeviceName = state.device.name
            )
          }
        } else {
          _uiState.update {
            it.copy(
              connectedDeviceMac = null,
              connectedDeviceName = null
            )
          }
        }
      }
    }
  }

  fun onMacAddressChanged(value: String) {
    _uiState.update { it.copy(macAddress = value.uppercase(), statusMessage = null) }
  }

  fun onNameChanged(value: String) {
    _uiState.update { it.copy(name = value, statusMessage = null) }
  }

  fun onDescriptionChanged(value: String) {
    _uiState.update { it.copy(description = value, statusMessage = null) }
  }

  fun refresh() {
    viewModelScope.launch { refreshDevices() }
  }

  fun useConnectedDevice() {
    val currentState = _uiState.value
    currentState.connectedDeviceMac?.let { mac ->
      _uiState.update {
        it.copy(
          macAddress = mac,
          name = currentState.connectedDeviceName ?: it.name,
          statusMessage = null
        )
      }
    }
  }

  fun submit() {
    viewModelScope.launch {
      if (!_uiState.value.isAuthenticated) {
        _uiState.update {
          it.copy(statusMessage = UiMessage.from(R.string.detector_management_guest_prompt))
        }
        return@launch
      }

      val macAddress = _uiState.value.macAddress.trim()
      val name = _uiState.value.name.trim()
      if (!MAC_ADDRESS_REGEX.matches(macAddress)) {
        _uiState.update {
          it.copy(statusMessage = UiMessage.from(R.string.detector_management_invalid_mac))
        }
        return@launch
      }
      if (name.isBlank()) {
        _uiState.update {
          it.copy(statusMessage = UiMessage.from(R.string.detector_management_name_required))
        }
        return@launch
      }

      _uiState.update { it.copy(isSubmitting = true, statusMessage = null) }
      when (val tokenResult = authRepository.ensureValidToken()) {
        is CosRayResult.Success -> {
          try {
            detectorManagementRepository.registerDevice(
              accessToken = tokenResult.data,
              macAddress = macAddress,
              name = name,
              description = _uiState.value.description.trim(),
            )
            _uiState.update {
              it.copy(
                isSubmitting = false,
                macAddress = "",
                name = "",
                description = "",
                statusMessage = UiMessage.from(R.string.detector_management_submit_success),
              )
            }
            refreshDevices()
          } catch (error: Throwable) {
            _uiState.update {
              it.copy(
                isSubmitting = false,
                statusMessage =
                  error.message?.let(UiMessage::fromRaw)
                    ?: UiMessage.from(R.string.detector_management_submit_failed),
              )
            }
          }
        }
        is CosRayResult.Error -> {
          _uiState.update {
            it.copy(
              isSubmitting = false,
              statusMessage =
                tokenResult.throwable.message?.let(UiMessage::fromRaw)
                  ?: UiMessage.from(R.string.detector_management_submit_failed),
            )
          }
        }
      }
    }
  }

  private suspend fun refreshDevices() {
    val currentState = _uiState.value
    if (!currentState.isAuthenticated) {
      _uiState.update { it.copy(isLoading = false, devices = emptyList()) }
      return
    }

    when (val tokenResult = authRepository.ensureValidToken()) {
      is CosRayResult.Success -> {
        try {
          val devices = detectorManagementRepository.getDevices(tokenResult.data)
          _uiState.update {
            it.copy(
              isLoading = false,
              devices =
                devices.map { device ->
                  ManagedDetector(
                    id = device.id,
                    name = device.name,
                    macAddress = device.macAddress,
                    description = device.description,
                    isActive = device.isActive,
                    ownerUsername = device.ownerUsername,
                    lastSeenAt = device.lastSeenAt,
                  )
                },
            )
          }
        } catch (error: Throwable) {
          _uiState.update {
            it.copy(
              isLoading = false,
              devices = emptyList(),
              statusMessage =
                error.message?.let(UiMessage::fromRaw)
                  ?: UiMessage.from(R.string.detector_management_load_failed),
            )
          }
        }
      }
      is CosRayResult.Error -> {
        _uiState.update {
          it.copy(
            isLoading = false,
            devices = emptyList(),
            statusMessage =
              tokenResult.throwable.message?.let(UiMessage::fromRaw)
                ?: UiMessage.from(R.string.detector_management_load_failed),
          )
        }
      }
    }
  }

  companion object {
    private val MAC_ADDRESS_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
  }
}

data class DetectorManagementUiState(
  val isAuthenticated: Boolean = false,
  val user: User? = null,
  val isLoading: Boolean = false,
  val isSubmitting: Boolean = false,
  val devices: List<ManagedDetector> = emptyList(),
  val macAddress: String = "",
  val name: String = "",
  val description: String = "",
  val statusMessage: UiMessage? = null,
  val connectedDeviceMac: String? = null,
  val connectedDeviceName: String? = null,
)

data class ManagedDetector(
  val id: Int,
  val name: String,
  val macAddress: String,
  val description: String?,
  val isActive: Boolean,
  val ownerUsername: String,
  val lastSeenAt: String?,
)
