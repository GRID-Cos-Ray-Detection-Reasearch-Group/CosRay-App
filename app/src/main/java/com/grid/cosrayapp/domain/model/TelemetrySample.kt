package com.grid.cosrayapp.domain.model

import java.time.Instant

data class TelemetrySample(
  val id: TelemetryId,
  val detectorId: DetectorId,
  val recordedAt: Instant,
  val acquisition: AcquisitionMetrics,
  val radiation: RadiationMetrics,
  val environment: EnvironmentSnapshot,
  val power: PowerSnapshot,
  val location: LocationSnapshot? = null,
  val acceleration: AccelerationSnapshot? = null,
  val sipmMonitoring: SipmMonitoring? = null,
  val packetMetadata: PacketMetadata? = null,
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
  val sipmTemperatureCelsius: Double? = null,
  val mcuTemperatureCelsius: Double? = null,
) {
  val primaryTemperatureCelsius: Double?
    get() = sipmTemperatureCelsius ?: boardTemperatureCelsius ?: sensorTemperatureCelsius
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

/**
 * GPS location data from the device
 *
 * @property longitudeDegrees Longitude in degrees (0°-360°), scaled from firmware value
 * @property latitudeDegrees Latitude in degrees (-90°-90°), scaled from firmware value
 * @property altitudeMeters Altitude in meters above sea level
 */
data class LocationSnapshot(
  val longitudeDegrees: Double,
  val latitudeDegrees: Double,
  val altitudeMeters: Int,
)

/**
 * 3-axis accelerometer data for device orientation
 *
 * @property xAxis Acceleration on X axis (scaled from firmware byte value)
 * @property yAxis Acceleration on Y axis (scaled from firmware byte value)
 * @property zAxis Acceleration on Z axis (scaled from firmware byte value)
 */
data class AccelerationSnapshot(val xAxis: Int, val yAxis: Int, val zAxis: Int) {
  /** Calculate magnitude of acceleration vector */
  val magnitude: Double
    get() = kotlin.math.sqrt((xAxis * xAxis + yAxis * yAxis + zAxis * zAxis).toDouble())
}

/**
 * Silicon Photomultiplier (SiPM) monitoring data
 *
 * @property currentMicroamps SiPM leakage current in microamps
 * @property voltageMillivolts SiPM bias voltage in millivolts
 */
data class SipmMonitoring(val currentMicroamps: Int, val voltageMillivolts: Int)

/**
 * Packet-level metadata for data validation and synchronization
 *
 * @property packetType Type of packet (MUON or TIMELINE)
 * @property packageCounter Global packet counter from firmware (persistent across resets)
 * @property cpuTime CPU clock ticks when data was captured
 * @property pps PPS (Pulse Per Second) counter for time synchronization
 * @property utcTimestamp UTC timestamp in milliseconds
 * @property crc CRC checksum value for packet validation
 * @property eventIndex Index of this event within the packet
 * @property eventCount Total number of events in the packet
 */
data class PacketMetadata(
  val packetType: PacketType,
  val packageCounter: Int,
  val cpuTime: Long,
  val pps: Int,
  val utcTimestamp: Long,
  val crc: Int? = null,
  val eventIndex: Int? = null,
  val eventCount: Int? = null,
)

enum class PacketType {
  MUON,
  TIMELINE,
}
