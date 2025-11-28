package com.travellerse.cosray_app.domain.model

import java.time.Instant

data class TelemetrySample(
  val id: TelemetryId,
  val detectorId: DetectorId,
  val recordedAt: Instant,
  val acquisition: AcquisitionMetrics,
  val radiation: RadiationMetrics,
  val environment: EnvironmentSnapshot,
  val power: PowerSnapshot,
  val diagnostics: DiagnosticsSnapshot = DiagnosticsSnapshot(),
)

data class AcquisitionMetrics(
  val particleCount: Int,
  val countsPerMinute: Int? = null,
  val integrationTimeSeconds: Double? = null,
  val deadTimeMillis: Int? = null,
  val coincidenceCount: Int? = null,
)

data class RadiationMetrics(
  val doseRateMicrosievertsPerHour: Double,
  val equivalentDoseMicrosieverts: Double? = null,
  val backgroundDoseMicrosievertsPerHour: Double? = null,
)

data class EnvironmentSnapshot(
  val boardTemperatureCelsius: Double? = null,
  val sensorTemperatureCelsius: Double? = null,
  val humidityPercent: Double? = null,
  val pressureHPa: Double? = null,
) {
  val primaryTemperatureCelsius: Double?
    get() = boardTemperatureCelsius ?: sensorTemperatureCelsius
}

data class PowerSnapshot(
  val batteryPercent: Int? = null,
  val batteryVoltage: Double? = null,
  val inputVoltage: Double? = null,
  val isCharging: Boolean? = null,
)

data class DiagnosticsSnapshot(
  val firmware: FirmwareVersion? = null,
  val uptimeMillis: Long? = null,
  val errorCode: Int? = null,
  val message: String? = null,
)
