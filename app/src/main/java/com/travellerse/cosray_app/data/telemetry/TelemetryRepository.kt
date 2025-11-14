package com.travellerse.cosray_app.data.telemetry

import com.travellerse.cosray_app.core.common.CosRayResult
import com.travellerse.cosray_app.core.common.runCosRayCatching
import com.travellerse.cosray_app.core.network.CosRayApi
import com.travellerse.cosray_app.core.network.model.TelemetryPayloadDto
import com.travellerse.cosray_app.data.auth.AuthRepository
import com.travellerse.cosray_app.data.ble.BleRepository
import com.travellerse.cosray_app.domain.model.BleDevice
import com.travellerse.cosray_app.domain.model.DeviceConnectionState
import com.travellerse.cosray_app.domain.model.TelemetrySample
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
        externalScope: CoroutineScope
) {

    private val _buffer = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val bufferedSamples: StateFlow<List<TelemetrySample>> = _buffer.asStateFlow()

    private val _liveTelemetry = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val liveTelemetry: StateFlow<List<TelemetrySample>> = _liveTelemetry.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    init {
        externalScope.launch {
            bleRepository.telemetry.collect { sample: TelemetrySample ->
                _buffer.update { list: List<TelemetrySample> ->
                    (list + sample).takeLast(BUFFER_SIZE)
                }
                _liveTelemetry.update { list: List<TelemetrySample> ->
                    (list + sample)
                            .sortedByDescending(TelemetrySample::recordedAt)
                            .take(MAX_LIVE_SAMPLES)
                }
            }
        }
        externalScope.launch {
            bleRepository.connectionState.collect { state: DeviceConnectionState ->
                when (state) {
                    is DeviceConnectionState.Connected -> _connectedDevice.value = state.device
                    else -> _connectedDevice.value = null
                }
            }
        }
    }

    suspend fun uploadBufferedSamples(): CosRayResult<Unit> {
        val samples = _buffer.value
        val device = _connectedDevice.value
        val tokens = authRepository.tokens.value
        return if (samples.isEmpty()) {
            CosRayResult.Success(Unit)
        } else if (tokens == null || device == null) {
            CosRayResult.Error(IllegalStateException("Missing authentication or device context"))
        } else {
            val payload =
                    TelemetryPayloadDto(
                            samples =
                                    samples.map { sample ->
                                        api.run { sample.toDto(device.id.value) }
                                    }
                    )
            val uploadResult = runCosRayCatching {
                api.uploadTelemetry(tokens.accessToken, payload)
            }
            if (uploadResult is CosRayResult.Success) {
                _buffer.value = emptyList()
                _liveTelemetry.value = emptyList()
            }
            uploadResult
        }
    }

    fun clearBuffer() {
        _buffer.value = emptyList()
    }

    companion object {
        private const val BUFFER_SIZE = 128
        private const val MAX_LIVE_SAMPLES = 30
    }
}
