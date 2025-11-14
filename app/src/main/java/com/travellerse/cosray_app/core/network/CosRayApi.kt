package com.travellerse.cosray_app.core.network

import com.travellerse.cosray_app.core.network.model.AuthResponse
import com.travellerse.cosray_app.core.network.model.LoginRequest
import com.travellerse.cosray_app.core.network.model.RegisterRequest
import com.travellerse.cosray_app.core.network.model.TelemetryPayloadDto
import com.travellerse.cosray_app.core.network.model.TelemetrySampleDto
import com.travellerse.cosray_app.core.network.model.UserResponse
import com.travellerse.cosray_app.domain.model.AcquisitionMetrics
import com.travellerse.cosray_app.domain.model.AuthTokens
import com.travellerse.cosray_app.domain.model.EnvironmentSnapshot
import com.travellerse.cosray_app.domain.model.PowerSnapshot
import com.travellerse.cosray_app.domain.model.RadiationMetrics
import com.travellerse.cosray_app.domain.model.TelemetrySample
import com.travellerse.cosray_app.domain.model.User
import com.travellerse.cosray_app.domain.model.UserId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
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
                val tokens = AuthTokens.fromSessionToken(response.meta.sessionToken)
                response.data.user.toDomain() to tokens
            }

    suspend fun register(
            email: String,
            password: String,
            displayName: String
    ): Pair<User, AuthTokens> =
            withContext(Dispatchers.IO) {
                val response: AuthResponse =
                        client
                                .post("/_allauth/app/v1/auth/signup") {
                                    contentType(ContentType.Application.Json)
                                    setBody(
                                            RegisterRequest(
                                                    email = email,
                                                    password = password,
                                                    displayName = displayName
                                            )
                                    )
                                }
                                .body()
                val tokens = AuthTokens.fromSessionToken(response.meta.sessionToken)
                response.data.user.toDomain() to tokens
            }

    suspend fun refreshToken(refreshToken: String): AuthTokens =
            withContext(Dispatchers.IO) { AuthTokens.fromSessionToken(refreshToken) }

    suspend fun fetchCurrentUser(accessToken: String): User =
            withContext(Dispatchers.IO) {
                val response: AuthResponse =
                        client
                                .get("/_allauth/app/v1/auth/session") { sessionToken(accessToken) }
                                .body()
                response.data.user.toDomain()
            }

    suspend fun uploadTelemetry(accessToken: String, payload: TelemetryPayloadDto): Unit =
            withContext(Dispatchers.IO) {
                client.post("/api/mu-packets/") {
                    sessionToken(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
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
                    diagnostics = diagnostics.toDto()
            )
}

fun UserResponse.toDomain(): User =
        User(
                id = UserId(id),
                email = email,
                displayName = display,
                avatarUrl = avatarUrl,
                organization = organization,
                roles = roles
        )

private fun AcquisitionMetrics.toDto(): TelemetrySampleDto.AcquisitionDto =
        TelemetrySampleDto.AcquisitionDto(
                particleCount = particleCount,
                countsPerMinute = countsPerMinute,
                integrationTimeSeconds = integrationTimeSeconds,
                deadTimeMillis = deadTimeMillis,
                coincidenceCount = coincidenceCount
        )

private fun RadiationMetrics.toDto(): TelemetrySampleDto.RadiationDto =
        TelemetrySampleDto.RadiationDto(
                doseRateMicrosievertsPerHour = doseRateMicrosievertsPerHour,
                equivalentDoseMicrosieverts = equivalentDoseMicrosieverts,
                backgroundDoseMicrosievertsPerHour = backgroundDoseMicrosievertsPerHour
        )

private fun EnvironmentSnapshot.toDto(): TelemetrySampleDto.EnvironmentDto =
        TelemetrySampleDto.EnvironmentDto(
                boardTemperatureCelsius = boardTemperatureCelsius,
                sensorTemperatureCelsius = sensorTemperatureCelsius,
                humidityPercent = humidityPercent,
                pressureHPa = pressureHPa
        )

private fun PowerSnapshot.toDto(): TelemetrySampleDto.PowerDto =
        TelemetrySampleDto.PowerDto(
                batteryPercent = batteryPercent,
                batteryVoltage = batteryVoltage,
                inputVoltage = inputVoltage,
                isCharging = isCharging
        )

private fun com.travellerse.cosray_app.domain.model.DiagnosticsSnapshot.toDto():
        TelemetrySampleDto.DiagnosticsDto =
        TelemetrySampleDto.DiagnosticsDto(
                firmwareVersion = firmware?.toString(),
                uptimeMillis = uptimeMillis,
                errorCode = errorCode,
                message = message
        )
