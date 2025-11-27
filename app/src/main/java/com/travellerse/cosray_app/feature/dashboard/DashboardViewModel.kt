package com.travellerse.cosray_app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travellerse.cosray_app.R
import com.travellerse.cosray_app.core.common.CosRayResult
import com.travellerse.cosray_app.core.ui.UiMessage
import com.travellerse.cosray_app.data.auth.AuthRepository
import com.travellerse.cosray_app.data.auth.AuthState
import com.travellerse.cosray_app.data.telemetry.TelemetryRepository
import com.travellerse.cosray_app.domain.model.BleDevice
import com.travellerse.cosray_app.domain.model.TelemetrySample
import com.travellerse.cosray_app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
        private val telemetryRepository: TelemetryRepository,
        private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                            telemetryRepository.liveTelemetry,
                            telemetryRepository.connectedDevice,
                            authRepository.authState
                    ) { samples, device, authState ->
                DashboardUiState(
                        user = (authState as? AuthState.Authenticated)?.user,
                        device = device,
                        samples = samples.take(MAX_SAMPLES),
                        isUploading = _uiState.value.isUploading,
                        uploadMessage = _uiState.value.uploadMessage
                )
            }
                    .collect { state -> _uiState.value = state }
        }
    }

    fun uploadBufferedTelemetry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadMessage = null) }
            when (val result = telemetryRepository.uploadBufferedSamples()) {
                is CosRayResult.Success ->
                        _uiState.update {
                            it.copy(
                                    isUploading = false,
                                    uploadMessage =
                                            UiMessage.from(R.string.dashboard_upload_success)
                            )
                        }
                is CosRayResult.Error ->
                        _uiState.update {
                            it.copy(
                                    isUploading = false,
                                    uploadMessage =
                                            result.throwable.message?.let(UiMessage::fromRaw)
                                                    ?: UiMessage.from(
                                                            R.string.dashboard_upload_failed
                                                    )
                            )
                        }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(uploadMessage = null) }
    }

    companion object {
        private const val MAX_SAMPLES = 30
    }
}

data class DashboardUiState(
        val user: User? = null,
        val device: BleDevice? = null,
        val samples: List<TelemetrySample> = emptyList(),
        val isUploading: Boolean = false,
        val uploadMessage: UiMessage? = null
)
