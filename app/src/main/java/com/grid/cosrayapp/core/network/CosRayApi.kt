package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.core.network.model.AuthResponse
import com.grid.cosrayapp.core.network.model.DeviceDto
import com.grid.cosrayapp.core.network.model.LoginRequest
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.PacketUploadResponse
import com.grid.cosrayapp.core.network.model.TokenRefreshRequest
import com.grid.cosrayapp.core.network.model.TokenResponse
import com.grid.cosrayapp.core.network.model.UpdateDeviceRequest
import com.grid.cosrayapp.core.network.model.UserResponse
import com.grid.cosrayapp.domain.model.AuthTokens
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

@Suppress("TooManyFunctions")
class CosRayApi(private val client: HttpClient) {
  suspend fun login(username: String, password: String): Pair<User, AuthTokens> =
    withContext(Dispatchers.IO) {
      val response: AuthResponse =
        client
          .post("/api/token/pair") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password))
          }
          .body()
      val tokens = AuthTokens.fromJwtTokens(response.access, response.refresh)
      val user = fetchCurrentUser(tokens.accessToken)
      user to tokens
    }

  suspend fun refreshToken(refreshToken: String): AuthTokens =
    withContext(Dispatchers.IO) {
      val response: TokenResponse =
        client
          .post("/api/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody(TokenRefreshRequest(refresh = refreshToken))
          }
          .body()
      AuthTokens.fromJwtTokens(accessToken = response.access, refreshToken = refreshToken)
    }

  suspend fun fetchCurrentUser(accessToken: String): User =
    withContext(Dispatchers.IO) {
      val response: UserResponse =
        client.get("/api/users/me") { bearerToken(accessToken) }.body()
      response.toDomain()
    }

  /** 获取当前用户的所有设备 */
  suspend fun getDevices(accessToken: String): List<DeviceDto> =
    withContext(Dispatchers.IO) { client.get("/api/devices/") { bearerToken(accessToken) }.body() }

  /** 获取指定设备的详情 */
  suspend fun getDevice(accessToken: String, deviceId: Int): DeviceDto =
    withContext(Dispatchers.IO) {
      client.get("/api/devices/$deviceId/") { bearerToken(accessToken) }.body()
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
      val updates =
        UpdateDeviceRequest(
          name = name,
          description = description,
          isActive = isActive,
        )
      client
        .patch("/api/devices/$deviceId/") {
          bearerToken(accessToken)
          contentType(ContentType.Application.Json)
          setBody(updates)
        }
        .body()
    }

  /** 删除设备 */
  suspend fun deleteDevice(accessToken: String, deviceId: Int): Unit =
    withContext(Dispatchers.IO) {
      client.delete("/api/devices/$deviceId/") { bearerToken(accessToken) }
    }

  /** 上传μ子或时间线数据包 */
  suspend fun uploadPacket(
    accessToken: String,
    request: PacketUploadRequest,
  ): PacketUploadResponse =
    withContext(Dispatchers.IO) {
      client
        .post("/api/mu-packets/") {
          bearerToken(accessToken)
          contentType(ContentType.Application.Json)
          setBody(request)
        }
        .body()
    }

  private fun HttpRequestBuilder.bearerToken(currentToken: String) {
    header("Authorization", "Bearer $currentToken")
  }
}

fun UserResponse.toDomain(): User =
  User(
    id = UserId(id.toString()),
    email = email,
    displayName = username,
  )
