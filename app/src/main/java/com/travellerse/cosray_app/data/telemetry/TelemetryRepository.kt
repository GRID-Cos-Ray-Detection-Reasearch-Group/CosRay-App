package com.travellerse.cosray_app.data.telemetry

import com.travellerse.cosray_app.core.ble.BleConnectionState
import com.travellerse.cosray_app.core.common.CosRayResult
import com.travellerse.cosray_app.core.common.runCosRayCatching
import com.travellerse.cosray_app.core.network.CosRayApi
import com.travellerse.cosray_app.core.network.model.PacketUploadRequest
import com.travellerse.cosray_app.core.network.model.PacketUploadResponse
import com.travellerse.cosray_app.core.network.model.TimelineEventDto
import com.travellerse.cosray_app.core.network.model.TimelinePacketDto
import com.travellerse.cosray_app.data.auth.AuthRepository
import com.travellerse.cosray_app.data.ble.BleRepository
import com.travellerse.cosray_app.domain.model.BleDevice
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
            bleRepository.connectionState.collect { state: BleConnectionState ->
                when (state) {
                    is BleConnectionState.Connected -> _connectedDevice.value = state.device
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
            // 将遥测数据转换为时间线数据包格式
            val timelineEvents =
                    samples.map { sample ->
                        val dto = api.run { sample.toDto(device.id.value) }
                        TimelineEventDto(
                                cpuTime = dto.recordedAt,
                                pps = dto.acquisition.countsPerMinute?.toLong() ?: 0L,
                                utc = dto.recordedAt,
                                ppsUtc = dto.recordedAt,
                                cputimePps = dto.recordedAt,
                                gpsLong = 0,
                                gpsLat = 0,
                                gpsAlt = 0,
                                accX = 0,
                                accY = 0,
                                accZ = 0,
                                siPMTmp = dto.environment?.sensorTemperatureCelsius?.toInt() ?: 0,
                                mcuTmp = dto.environment?.boardTemperatureCelsius?.toInt() ?: 0,
                                siPMImon = dto.power?.batteryPercent ?: 0,
                                siPMVmon = dto.power?.batteryVoltage?.toInt() ?: 0,
                                timestamp = dto.recordedAt
                        )
                    }

            val timelinePacket =
                    TimelinePacketDto(
                            packageCounter = samples.size.toLong(),
                            events = timelineEvents
                    )

            val request =
                    PacketUploadRequest(
                            device = device.id.value,
                            packetType = "timeline",
                            timelinePacket = timelinePacket
                    )

            val uploadResult: CosRayResult<PacketUploadResponse> = runCosRayCatching {
                api.uploadPacket(tokens.accessToken, request)
            }
            if (uploadResult is CosRayResult.Success) {
                _buffer.value = emptyList()
                _liveTelemetry.value = emptyList()
                CosRayResult.Success(Unit)
            } else {
                uploadResult as CosRayResult<Unit>
            }
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
