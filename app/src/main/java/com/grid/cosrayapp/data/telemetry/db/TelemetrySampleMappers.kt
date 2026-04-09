package com.grid.cosrayapp.data.telemetry.db

import com.grid.cosrayapp.domain.model.AccelerationSnapshot
import com.grid.cosrayapp.domain.model.AcquisitionMetrics
import com.grid.cosrayapp.domain.model.DetectorId
import com.grid.cosrayapp.domain.model.DiagnosticsSnapshot
import com.grid.cosrayapp.domain.model.EnvironmentSnapshot
import com.grid.cosrayapp.domain.model.FirmwareVersion
import com.grid.cosrayapp.domain.model.LocationSnapshot
import com.grid.cosrayapp.domain.model.PacketMetadata
import com.grid.cosrayapp.domain.model.PowerSnapshot
import com.grid.cosrayapp.domain.model.RadiationMetrics
import com.grid.cosrayapp.domain.model.SipmMonitoring
import com.grid.cosrayapp.domain.model.TelemetryId
import com.grid.cosrayapp.domain.model.TelemetrySample
import java.time.Instant

internal fun TelemetrySample.toEntity(): TelemetrySampleEntity {
  val metadata = packetMetadata
  val environmentSnapshot = environment
  val locationSnapshot = location
  val accelerationSnapshot = acceleration
  val sipm = sipmMonitoring
  val diagnosticsSnapshot = diagnostics

  return TelemetrySampleEntity(
    telemetryId = id.value,
    detectorId = detectorId.value,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    packetType = metadata?.packetType,
    packageCounter = metadata?.packageCounter,
    cpuTime = metadata?.cpuTime,
    pps = metadata?.pps,
    utcTimestampMillis = metadata?.utcTimestamp,
    crc = metadata?.crc,
    eventIndex = metadata?.eventIndex,
    eventCount = metadata?.eventCount,
    particleCount = acquisition.particleCount,
    countsPerMinute = acquisition.countsPerMinute,
    integrationTimeSeconds = acquisition.integrationTimeSeconds,
    deadTimeMillis = acquisition.deadTimeMillis,
    coincidenceCount = acquisition.coincidenceCount,
    doseRateMicrosievertsPerHour = radiation.doseRateMicrosievertsPerHour,
    equivalentDoseMicrosieverts = radiation.equivalentDoseMicrosieverts,
    backgroundDoseMicrosievertsPerHour = radiation.backgroundDoseMicrosievertsPerHour,
    boardTemperatureCelsius = environmentSnapshot.boardTemperatureCelsius,
    sensorTemperatureCelsius = environmentSnapshot.sensorTemperatureCelsius,
    humidityPercent = environmentSnapshot.humidityPercent,
    pressureHPa = environmentSnapshot.pressureHPa,
    sipmTemperatureCelsius = environmentSnapshot.sipmTemperatureCelsius,
    mcuTemperatureCelsius = environmentSnapshot.mcuTemperatureCelsius,
    batteryPercent = power.batteryPercent,
    batteryVoltage = power.batteryVoltage,
    inputVoltage = power.inputVoltage,
    isCharging = power.isCharging,
    longitudeDegrees = locationSnapshot?.longitudeDegrees,
    latitudeDegrees = locationSnapshot?.latitudeDegrees,
    altitudeMeters = locationSnapshot?.altitudeMeters,
    accX = accelerationSnapshot?.xAxis,
    accY = accelerationSnapshot?.yAxis,
    accZ = accelerationSnapshot?.zAxis,
    sipmCurrentMicroamps = sipm?.currentMicroamps,
    sipmVoltageMillivolts = sipm?.voltageMillivolts,
    firmwareVersion = diagnosticsSnapshot.firmware?.toString(),
    uptimeMillis = diagnosticsSnapshot.uptimeMillis,
    errorCode = diagnosticsSnapshot.errorCode,
    diagnosticMessage = diagnosticsSnapshot.message,
  )
}

internal fun TelemetrySampleEntity.toDomain(): TelemetrySample {
  val recordedAtInstant = Instant.ofEpochMilli(recordedAtEpochMillis)

  val acquisitionMetrics =
    AcquisitionMetrics(
      particleCount = particleCount,
      countsPerMinute = countsPerMinute,
      integrationTimeSeconds = integrationTimeSeconds,
      deadTimeMillis = deadTimeMillis,
      coincidenceCount = coincidenceCount,
    )

  val radiationMetrics =
    RadiationMetrics(
      doseRateMicrosievertsPerHour = doseRateMicrosievertsPerHour,
      equivalentDoseMicrosieverts = equivalentDoseMicrosieverts,
      backgroundDoseMicrosievertsPerHour = backgroundDoseMicrosievertsPerHour,
    )

  val environmentSnapshot =
    EnvironmentSnapshot(
      boardTemperatureCelsius = boardTemperatureCelsius,
      sensorTemperatureCelsius = sensorTemperatureCelsius,
      humidityPercent = humidityPercent,
      pressureHPa = pressureHPa,
      sipmTemperatureCelsius = sipmTemperatureCelsius,
      mcuTemperatureCelsius = mcuTemperatureCelsius,
    )

  val powerSnapshot =
    PowerSnapshot(
      batteryPercent = batteryPercent,
      batteryVoltage = batteryVoltage,
      inputVoltage = inputVoltage,
      isCharging = isCharging,
    )

  val locationSnapshot =
    if (longitudeDegrees != null && latitudeDegrees != null && altitudeMeters != null) {
      LocationSnapshot(
        longitudeDegrees = longitudeDegrees,
        latitudeDegrees = latitudeDegrees,
        altitudeMeters = altitudeMeters,
      )
    } else {
      null
    }

  val accelerationSnapshot =
    if (accX != null && accY != null && accZ != null) {
      AccelerationSnapshot(xAxis = accX, yAxis = accY, zAxis = accZ)
    } else {
      null
    }

  val sipm =
    if (sipmCurrentMicroamps != null && sipmVoltageMillivolts != null) {
      SipmMonitoring(currentMicroamps = sipmCurrentMicroamps, voltageMillivolts = sipmVoltageMillivolts)
    } else {
      null
    }

  val diagnosticsSnapshot =
    DiagnosticsSnapshot(
      firmware = firmwareVersion?.let { FirmwareVersion(versionName = it.substringBefore('+'), buildMetadata = it.substringAfter('+', missingDelimiterValue = "").ifBlank { null }) },
      uptimeMillis = uptimeMillis,
      errorCode = errorCode,
      message = diagnosticMessage,
    )

  val packet =
    if (packetType != null && packageCounter != null && cpuTime != null && pps != null && utcTimestampMillis != null) {
      PacketMetadata(
        packetType = packetType,
        packageCounter = packageCounter,
        cpuTime = cpuTime,
        pps = pps,
        utcTimestamp = utcTimestampMillis,
        crc = crc,
        eventIndex = eventIndex,
        eventCount = eventCount,
      )
    } else {
      null
    }

  return TelemetrySample(
    id = TelemetryId(telemetryId),
    detectorId = DetectorId(detectorId),
    recordedAt = recordedAtInstant,
    acquisition = acquisitionMetrics,
    radiation = radiationMetrics,
    environment = environmentSnapshot,
    power = powerSnapshot,
    location = locationSnapshot,
    acceleration = accelerationSnapshot,
    sipmMonitoring = sipm,
    packetMetadata = packet,
    diagnostics = diagnosticsSnapshot,
  )
}
