package com.travellerse.cosray_app.core.ble

import com.travellerse.cosray_app.domain.model.AcquisitionMetrics
import com.travellerse.cosray_app.domain.model.BleDevice
import com.travellerse.cosray_app.domain.model.DetectorId
import com.travellerse.cosray_app.domain.model.DiagnosticsSnapshot
import com.travellerse.cosray_app.domain.model.EnvironmentSnapshot
import com.travellerse.cosray_app.domain.model.FirmwareVersion
import com.travellerse.cosray_app.domain.model.PowerSnapshot
import com.travellerse.cosray_app.domain.model.RadiationMetrics
import com.travellerse.cosray_app.domain.model.TelemetryId
import com.travellerse.cosray_app.domain.model.TelemetrySample
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BleTelemetryParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: ByteArray, device: BleDevice?): TelemetrySample? {
        if (raw.isEmpty()) return null
        val payload = raw.decodeToString().trim()
        if (payload.isEmpty()) return null
        return runCatching { json.decodeFromString(BleTelemetryDto.serializer(), payload) }
                .mapCatching { dto -> dto.toDomain(device) }
                .getOrNull()
    }

    @Serializable
    private data class BleTelemetryDto(
            @SerialName("detector_id") val detectorId: String? = null,
            @SerialName("device_id") val deviceId: String? = null,
            @SerialName("telemetry_id") val telemetryId: String? = null,
            @SerialName("recorded_at") val recordedAtMillis: Long? = null,
            @SerialName("timestamp") val timestampMillis: Long? = null,
            val acquisition: AcquisitionDto? = null,
            val radiation: RadiationDto? = null,
            val environment: EnvironmentDto? = null,
            val power: PowerDto? = null,
            val diagnostics: DiagnosticsDto? = null,
            @SerialName("particle_count") val particleCount: Int? = null,
            @SerialName("counts_per_minute") val countsPerMinute: Int? = null,
            @SerialName("integration_time_seconds") val integrationTimeSeconds: Double? = null,
            @SerialName("dead_time_millis") val deadTimeMillis: Int? = null,
            @SerialName("coincidence_count") val coincidenceCount: Int? = null,
            @SerialName("radiation_usvh") val radiationDoseRateUsvh: Double? = null,
            @SerialName("equivalent_dose_usv") val equivalentDoseUsv: Double? = null,
            @SerialName("background_dose_usvh") val backgroundDoseUsvh: Double? = null,
            @SerialName("board_temp_c") val boardTemperatureCelsius: Double? = null,
            @SerialName("sensor_temp_c") val sensorTemperatureCelsius: Double? = null,
            @SerialName("humidity_percent") val humidityPercent: Double? = null,
            @SerialName("pressure_hpa") val pressureHPa: Double? = null,
            @SerialName("battery_percent") val batteryPercent: Int? = null,
            @SerialName("battery_voltage_v") val batteryVoltage: Double? = null,
            @SerialName("input_voltage_v") val inputVoltage: Double? = null,
            @SerialName("is_charging") val isCharging: Boolean? = null,
            @SerialName("firmware_version") val firmwareVersion: String? = null,
            @SerialName("uptime_millis") val uptimeMillis: Long? = null,
            @SerialName("error_code") val errorCode: Int? = null,
            val message: String? = null
    ) {
        @Serializable
        data class AcquisitionDto(
                @SerialName("particle_count") val particleCount: Int,
                @SerialName("counts_per_minute") val countsPerMinute: Int? = null,
                @SerialName("integration_time_seconds") val integrationTimeSeconds: Double? = null,
                @SerialName("dead_time_millis") val deadTimeMillis: Int? = null,
                @SerialName("coincidence_count") val coincidenceCount: Int? = null
        )

        @Serializable
        data class RadiationDto(
                @SerialName("dose_rate_usvh") val doseRateMicrosievertsPerHour: Double,
                @SerialName("equivalent_dose_usv") val equivalentDoseMicrosieverts: Double? = null,
                @SerialName("background_dose_usvh")
                val backgroundDoseMicrosievertsPerHour: Double? = null
        )

        @Serializable
        data class EnvironmentDto(
                @SerialName("board_temp_c") val boardTemperatureCelsius: Double? = null,
                @SerialName("sensor_temp_c") val sensorTemperatureCelsius: Double? = null,
                @SerialName("humidity_percent") val humidityPercent: Double? = null,
                @SerialName("pressure_hpa") val pressureHPa: Double? = null
        )

        @Serializable
        data class PowerDto(
                @SerialName("battery_percent") val batteryPercent: Int? = null,
                @SerialName("battery_voltage_v") val batteryVoltage: Double? = null,
                @SerialName("input_voltage_v") val inputVoltage: Double? = null,
                @SerialName("is_charging") val isCharging: Boolean? = null
        )

        @Serializable
        data class DiagnosticsDto(
                @SerialName("firmware_version") val firmwareVersion: String? = null,
                @SerialName("uptime_millis") val uptimeMillis: Long? = null,
                @SerialName("error_code") val errorCode: Int? = null,
                val message: String? = null
        )
    }

    private fun BleTelemetryDto.toDomain(device: BleDevice?): TelemetrySample {
        val resolvedRecordedAtMillis =
                recordedAtMillis
                        ?: timestampMillis ?: device?.lastSeen?.toEpochMilli()
                                ?: System.currentTimeMillis()
        val rawDetectorId =
                listOf(detectorId, deviceId, device?.id?.value, device?.macAddress)
                        .firstOrNull { !it.isNullOrBlank() }
                        ?.trim()
                        ?: error("Detector identifier missing in telemetry payload")
        val detector = DetectorId(rawDetectorId)
        val resolvedTelemetryId =
                telemetryId?.takeIf { it.isNotBlank() }?.let(::TelemetryId)
                        ?: TelemetryId.fromTimestamp(resolvedRecordedAtMillis, detector)

        val acquisitionDto = acquisition
        val particleCount = acquisitionDto?.particleCount ?: particleCount ?: 0
        val resolvedCountsPerMinute = acquisitionDto?.countsPerMinute ?: countsPerMinute
        val resolvedIntegrationSeconds =
                acquisitionDto?.integrationTimeSeconds ?: integrationTimeSeconds
        val resolvedDeadTimeMillis = acquisitionDto?.deadTimeMillis ?: deadTimeMillis
        val resolvedCoincidenceCount = acquisitionDto?.coincidenceCount ?: coincidenceCount
        val acquisitionMetrics =
                AcquisitionMetrics(
                        particleCount = particleCount,
                        countsPerMinute = resolvedCountsPerMinute,
                        integrationTimeSeconds = resolvedIntegrationSeconds,
                        deadTimeMillis = resolvedDeadTimeMillis,
                        coincidenceCount = resolvedCoincidenceCount
                )

        val radiationDto = radiation
        val doseRate = radiationDto?.doseRateMicrosievertsPerHour ?: radiationDoseRateUsvh ?: 0.0
        val radiationMetrics =
                RadiationMetrics(
                        doseRateMicrosievertsPerHour = doseRate,
                        equivalentDoseMicrosieverts = radiationDto?.equivalentDoseMicrosieverts
                                        ?: equivalentDoseUsv,
                        backgroundDoseMicrosievertsPerHour =
                                radiationDto?.backgroundDoseMicrosievertsPerHour
                                        ?: backgroundDoseUsvh
                )

        val environmentDto = environment
        val environmentSnapshot =
                EnvironmentSnapshot(
                        boardTemperatureCelsius = environmentDto?.boardTemperatureCelsius
                                        ?: boardTemperatureCelsius,
                        sensorTemperatureCelsius = environmentDto?.sensorTemperatureCelsius
                                        ?: sensorTemperatureCelsius,
                        humidityPercent = environmentDto?.humidityPercent ?: humidityPercent,
                        pressureHPa = environmentDto?.pressureHPa ?: pressureHPa
                )

        val powerDto = power
        val powerSnapshot =
                PowerSnapshot(
                        batteryPercent = powerDto?.batteryPercent ?: batteryPercent,
                        batteryVoltage = powerDto?.batteryVoltage ?: batteryVoltage,
                        inputVoltage = powerDto?.inputVoltage ?: inputVoltage,
                        isCharging = powerDto?.isCharging ?: isCharging
                )

        val diagnosticsDto = diagnostics
        val firmwareValue = diagnosticsDto?.firmwareVersion ?: firmwareVersion
        val diagnosticsSnapshot =
                DiagnosticsSnapshot(
                        firmware = firmwareValue.toFirmwareVersion(),
                        uptimeMillis = diagnosticsDto?.uptimeMillis ?: uptimeMillis,
                        errorCode = diagnosticsDto?.errorCode ?: errorCode,
                        message = diagnosticsDto?.message ?: message
                )

        return TelemetrySample(
                id = resolvedTelemetryId,
                detectorId = detector,
                recordedAt = Instant.ofEpochMilli(resolvedRecordedAtMillis),
                acquisition = acquisitionMetrics,
                radiation = radiationMetrics,
                environment = environmentSnapshot,
                power = powerSnapshot,
                diagnostics = diagnosticsSnapshot
        )
    }

    private fun String?.toFirmwareVersion(): FirmwareVersion? {
        if (this.isNullOrBlank()) return null
        val parts = this.split('+', limit = 2)
        val versionName = parts.first().trim()
        if (versionName.isEmpty()) return null
        val metadata = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.trim()
        return FirmwareVersion(versionName, metadata)
    }
}
