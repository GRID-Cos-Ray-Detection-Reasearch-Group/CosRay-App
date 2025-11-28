package com.travellerse.cosray_app.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetrySampleDto(
  @SerialName("detector_id") val detectorId: String,
  @SerialName("device_id") val deviceId: String,
  @SerialName("telemetry_id") val telemetryId: String,
  @SerialName("recorded_at") val recordedAt: Long,
  val acquisition: AcquisitionDto,
  val radiation: RadiationDto,
  val environment: EnvironmentDto? = null,
  val power: PowerDto? = null,
  val diagnostics: DiagnosticsDto? = null,
) {
  @Serializable
  data class AcquisitionDto(
    @SerialName("particle_count") val particleCount: Int,
    @SerialName("counts_per_minute") val countsPerMinute: Int? = null,
    @SerialName("integration_time_seconds") val integrationTimeSeconds: Double? = null,
    @SerialName("dead_time_millis") val deadTimeMillis: Int? = null,
    @SerialName("coincidence_count") val coincidenceCount: Int? = null,
  )

  @Serializable
  data class RadiationDto(
    @SerialName("dose_rate_usvh") val doseRateMicrosievertsPerHour: Double,
    @SerialName("equivalent_dose_usv") val equivalentDoseMicrosieverts: Double? = null,
    @SerialName("background_dose_usvh") val backgroundDoseMicrosievertsPerHour: Double? = null,
  )

  @Serializable
  data class EnvironmentDto(
    @SerialName("board_temp_c") val boardTemperatureCelsius: Double? = null,
    @SerialName("sensor_temp_c") val sensorTemperatureCelsius: Double? = null,
    @SerialName("humidity_percent") val humidityPercent: Double? = null,
    @SerialName("pressure_hpa") val pressureHPa: Double? = null,
  )

  @Serializable
  data class PowerDto(
    @SerialName("battery_percent") val batteryPercent: Int? = null,
    @SerialName("battery_voltage_v") val batteryVoltage: Double? = null,
    @SerialName("input_voltage_v") val inputVoltage: Double? = null,
    @SerialName("is_charging") val isCharging: Boolean? = null,
  )

  @Serializable
  data class DiagnosticsDto(
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("uptime_millis") val uptimeMillis: Long? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val message: String? = null,
  )
}

@Serializable data class TelemetryPayloadDto(val samples: List<TelemetrySampleDto>)
