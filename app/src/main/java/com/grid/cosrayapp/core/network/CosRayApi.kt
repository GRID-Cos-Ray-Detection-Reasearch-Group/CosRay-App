package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.core.network.model.AuthResponse
import com.grid.cosrayapp.core.network.model.DeviceDto
import com.grid.cosrayapp.core.network.model.LoginRequest
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.PacketUploadResponse
import com.grid.cosrayapp.core.network.model.RegisterDeviceRequest
import com.grid.cosrayapp.core.network.model.RegisterRequest
import com.grid.cosrayapp.core.network.model.TelemetryPayloadDto
import com.grid.cosrayapp.core.network.model.TelemetrySampleDto
import com.grid.cosrayapp.core.network.model.UserResponse
import com.grid.cosrayapp.domain.model.AcquisitionMetrics
import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.EnvironmentSnapshot
import com.grid.cosrayapp.domain.model.PowerSnapshot
import com.grid.cosrayapp.domain.model.RadiationMetrics
import com.grid.cosrayapp.domain.model.TelemetrySample
import com.grid.cosrayapp.domain.model.User
import com.grid.cosrayapp.domain.model.UserId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CosRayApi(private val client: HttpClient) {
  // https://docs.allauth.org/en/dev/headless/openapi-specification/#tag/Authentication:-Account
  suspend fun login(username: String, password: String): Pair<User, AuthTokens> =
    withContext(Dispatchers.IO) {
      val response: AuthResponse =
        client
          .post("/_allauth/app/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password))
          }
          .body()
      val sessionToken =
        response.meta.sessionToken
          ?: throw IllegalStateException("Session token not found in login response")
      val tokens = AuthTokens.fromSessionToken(sessionToken)
      response.data.user.toDomain() to tokens
    }

  suspend fun register(
    email: String,
    password: String,
    displayName: String,
  ): Pair<User, AuthTokens> =
    withContext(Dispatchers.IO) {
      val response: AuthResponse =
        client
          .post("/_allauth/app/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = password, displayName = displayName))
          }
          .body()
      val sessionToken =
        response.meta.sessionToken
          ?: throw IllegalStateException("Session token not found in register response")
      val tokens = AuthTokens.fromSessionToken(sessionToken)
      response.data.user.toDomain() to tokens
    }

  suspend fun refreshToken(refreshToken: String): AuthTokens =
    withContext(Dispatchers.IO) { AuthTokens.fromSessionToken(refreshToken) }

  suspend fun fetchCurrentUser(accessToken: String): User =
    withContext(Dispatchers.IO) {
      val response: AuthResponse =
        client.get("/_allauth/app/v1/auth/session") { sessionToken(accessToken) }.body()
      response.data.user.toDomain()
    }

  /**
   * 上传遥测数据（旧版 API，用于 TelemetryRepository）
   *
   * @param accessToken 访问令牌
   * @param payload 遥测数据负载
   * @deprecated 使用 uploadPacket 替代
   */
  @Deprecated("Use uploadPacket instead", ReplaceWith("uploadPacket(accessToken, request)"))
  suspend fun uploadTelemetry(accessToken: String, payload: TelemetryPayloadDto): Unit =
    withContext(Dispatchers.IO) {
      client.post("/api/mu-packets/") {
        sessionToken(accessToken)
        contentType(ContentType.Application.Json)
        setBody(payload)
      }
    }

  /** 注册新设备 */
  suspend fun registerDevice(
    accessToken: String,
    macAddress: String,
    name: String,
    description: String? = null,
  ): DeviceDto =
    withContext(Dispatchers.IO) {
      val request =
        RegisterDeviceRequest(macAddress = macAddress, name = name, description = description)
      client
        .post("/api/devices/") {
          sessionToken(accessToken)
          contentType(ContentType.Application.Json)
          setBody(request)
        }
        .body()
    }

  /** 获取当前用户的所有设备 */
  suspend fun getDevices(accessToken: String): List<DeviceDto> =
    withContext(Dispatchers.IO) { client.get("/api/devices/") { sessionToken(accessToken) }.body() }

  /** 获取指定设备的详情 */
  suspend fun getDevice(accessToken: String, deviceId: Int): DeviceDto =
    withContext(Dispatchers.IO) {
      client.get("/api/devices/$deviceId/") { sessionToken(accessToken) }.body()
    }

  /** 更新设备信息 */
  suspend fun updateDevice(
    accessToken: String,
    deviceId: Int,
    name: String? = null,
    description: String? = null,
    isActive: Boolean? = null,
  ): DeviceDto =
    withContext(Dispatchers.IO) {
      val updates = buildMap {
        name?.let { put("name", it) }
        description?.let { put("description", it) }
        isActive?.let { put("is_active", it) }
      }
      client
        .patch("/api/devices/$deviceId/") {
          sessionToken(accessToken)
          contentType(ContentType.Application.Json)
          setBody(updates)
        }
        .body()
    }

  /** 删除设备 */
  suspend fun deleteDevice(accessToken: String, deviceId: Int): Unit =
    withContext(Dispatchers.IO) {
      client.delete("/api/devices/$deviceId/") { sessionToken(accessToken) }
    }

  /** 上传μ子或时间线数据包 */
  suspend fun uploadPacket(
    accessToken: String,
    request: PacketUploadRequest,
  ): PacketUploadResponse =
    withContext(Dispatchers.IO) {
      client
        .post("/api/mu-packets/") {
          sessionToken(accessToken)
          contentType(ContentType.Application.Json)
          setBody(request)
        }
        .body()
    }

  private fun HttpRequestBuilder.sessionToken(currentToken: String) {
    header("X-Session-Token", currentToken)
  }

  fun TelemetrySample.toDto(deviceId: String): TelemetrySampleDto =
    TelemetrySampleDto(
      detectorId = detectorId.value,
      deviceId = deviceId,
      telemetryId = id.value,
      recordedAt = recordedAt.toEpochMilli(),
      acquisition = acquisition.toDto(),
      radiation = radiation.toDto(),
      environment = environment.toDto(),
      power = power.toDto(),
      diagnostics = diagnostics.toDto(),
    )
}

fun UserResponse.toDomain(): User =
  User(
    id = UserId(id),
    email = email,
    displayName = display,
    avatarUrl = avatarUrl,
    organization = organization,
    roles = roles,
  )

private fun AcquisitionMetrics.toDto(): TelemetrySampleDto.AcquisitionDto =
  TelemetrySampleDto.AcquisitionDto(
    particleCount = particleCount,
    countsPerMinute = countsPerMinute,
    integrationTimeSeconds = integrationTimeSeconds,
    deadTimeMillis = deadTimeMillis,
    coincidenceCount = coincidenceCount,
  )

private fun RadiationMetrics.toDto(): TelemetrySampleDto.RadiationDto =
  TelemetrySampleDto.RadiationDto(
    doseRateMicrosievertsPerHour = doseRateMicrosievertsPerHour,
    equivalentDoseMicrosieverts = equivalentDoseMicrosieverts,
    backgroundDoseMicrosievertsPerHour = backgroundDoseMicrosievertsPerHour,
  )

private fun EnvironmentSnapshot.toDto(): TelemetrySampleDto.EnvironmentDto =
  TelemetrySampleDto.EnvironmentDto(
    boardTemperatureCelsius = boardTemperatureCelsius,
    sensorTemperatureCelsius = sensorTemperatureCelsius,
    humidityPercent = humidityPercent,
    pressureHPa = pressureHPa,
  )

private fun PowerSnapshot.toDto(): TelemetrySampleDto.PowerDto =
  TelemetrySampleDto.PowerDto(
    batteryPercent = batteryPercent,
    batteryVoltage = batteryVoltage,
    inputVoltage = inputVoltage,
    isCharging = isCharging,
  )

private fun com.grid.cosrayapp.domain.model.DiagnosticsSnapshot.toDto():
  TelemetrySampleDto.DiagnosticsDto =
  TelemetrySampleDto.DiagnosticsDto(
    firmwareVersion = firmware?.toString(),
    uptimeMillis = uptimeMillis,
    errorCode = errorCode,
    message = message,
  )
