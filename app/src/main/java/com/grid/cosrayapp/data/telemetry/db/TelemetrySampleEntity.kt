package com.grid.cosrayapp.data.telemetry.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.grid.cosrayapp.domain.model.PacketType
import java.time.Instant

@Entity(
  tableName = "telemetry_samples",
  indices =
    [
      Index(
        value = ["detector_id", "recorded_at_epoch_millis"],
        orders = [Index.Order.ASC, Index.Order.DESC],
      ),
      Index(
        value = ["detector_id", "packet_type", "recorded_at_epoch_millis"],
        orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC],
      ),
      Index(
        value = ["packet_type", "recorded_at_epoch_millis"],
        orders = [Index.Order.ASC, Index.Order.DESC],
      ),
    ],
)
data class TelemetrySampleEntity(
  @PrimaryKey
  @ColumnInfo(name = "telemetry_id")
  val telemetryId: String,
  @ColumnInfo(name = "detector_id") val detectorId: String,
  @ColumnInfo(name = "recorded_at_epoch_millis") val recordedAtEpochMillis: Long,
  @ColumnInfo(name = "packet_type") val packetType: PacketType?,
  @ColumnInfo(name = "package_counter") val packageCounter: Int?,
  @ColumnInfo(name = "cpu_time") val cpuTime: Long?,
  @ColumnInfo(name = "pps") val pps: Int?,
  @ColumnInfo(name = "utc_timestamp_millis") val utcTimestampMillis: Long?,
  @ColumnInfo(name = "crc") val crc: Int?,
  @ColumnInfo(name = "event_index") val eventIndex: Int?,
  @ColumnInfo(name = "event_count") val eventCount: Int?,
  @ColumnInfo(name = "particle_count") val particleCount: Int,
  @ColumnInfo(name = "counts_per_minute") val countsPerMinute: Int?,
  @ColumnInfo(name = "integration_time_seconds") val integrationTimeSeconds: Double?,
  @ColumnInfo(name = "dead_time_millis") val deadTimeMillis: Int?,
  @ColumnInfo(name = "coincidence_count") val coincidenceCount: Int?,
  @ColumnInfo(name = "dose_rate_usvh") val doseRateMicrosievertsPerHour: Double,
  @ColumnInfo(name = "equivalent_dose_usv") val equivalentDoseMicrosieverts: Double?,
  @ColumnInfo(name = "background_dose_usvh") val backgroundDoseMicrosievertsPerHour: Double?,
  @ColumnInfo(name = "board_temp_c") val boardTemperatureCelsius: Double?,
  @ColumnInfo(name = "sensor_temp_c") val sensorTemperatureCelsius: Double?,
  @ColumnInfo(name = "humidity_percent") val humidityPercent: Double?,
  @ColumnInfo(name = "pressure_hpa") val pressureHPa: Double?,
  @ColumnInfo(name = "sipm_temp_c") val sipmTemperatureCelsius: Double?,
  @ColumnInfo(name = "mcu_temp_c") val mcuTemperatureCelsius: Double?,
  @ColumnInfo(name = "battery_percent") val batteryPercent: Int?,
  @ColumnInfo(name = "battery_voltage") val batteryVoltage: Double?,
  @ColumnInfo(name = "input_voltage") val inputVoltage: Double?,
  @ColumnInfo(name = "is_charging") val isCharging: Boolean?,
  @ColumnInfo(name = "longitude_degrees") val longitudeDegrees: Double?,
  @ColumnInfo(name = "latitude_degrees") val latitudeDegrees: Double?,
  @ColumnInfo(name = "altitude_meters") val altitudeMeters: Int?,
  @ColumnInfo(name = "acc_x") val accX: Int?,
  @ColumnInfo(name = "acc_y") val accY: Int?,
  @ColumnInfo(name = "acc_z") val accZ: Int?,
  @ColumnInfo(name = "sipm_current_uA") val sipmCurrentMicroamps: Int?,
  @ColumnInfo(name = "sipm_voltage_mV") val sipmVoltageMillivolts: Int?,
  @ColumnInfo(name = "firmware_version") val firmwareVersion: String?,
  @ColumnInfo(name = "uptime_millis") val uptimeMillis: Long?,
  @ColumnInfo(name = "error_code") val errorCode: Int?,
  @ColumnInfo(name = "diagnostic_message") val diagnosticMessage: String?,
  @ColumnInfo(name = "is_uploaded", defaultValue = "0") val isUploaded: Boolean = false,
) {
  val recordedAt: Instant
    get() = Instant.ofEpochMilli(recordedAtEpochMillis)
}
