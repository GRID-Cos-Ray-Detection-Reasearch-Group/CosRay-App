package com.grid.cosrayapp.integration

import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.model.DeviceDto
import com.grid.cosrayapp.core.network.model.MuonEventDto
import com.grid.cosrayapp.core.network.model.MuonPacketDto
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.TimelineEventDto
import com.grid.cosrayapp.core.network.model.TimelinePacketDto
import com.grid.cosrayapp.domain.model.AuthTokens
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosRayApiBackendIntegrationTest {
  private val baseUrl = "http://127.0.0.1:8000"

  @Test
  fun full_backend_flow_with_real_api_should_pass() = runBlocking {
    val httpClient =
            HttpClient(Java) {
              expectSuccess = true
              install(ContentNegotiation) {
                json(
                        Json {
                          ignoreUnknownKeys = true
                          isLenient = true
                          encodeDefaults = true
                        }
                )
              }
              defaultRequest { url(baseUrl) }
            }

    try {
      val api = CosRayApi(httpClient)
      val tokens = testAuthFlow(api)
      val targetDevice = testDeviceCreationAndUpdate(api, httpClient, tokens.accessToken)
      testMuonUpload(api, tokens.accessToken, targetDevice.macAddress)
      testTimelineUpload(api, tokens.accessToken, targetDevice.macAddress)
      api.deleteDevice(tokens.accessToken, targetDevice.id)
    } finally {
      httpClient.close()
    }
  }

  private suspend fun testAuthFlow(api: CosRayApi): AuthTokens {
    val (user, tokens) = api.login("itest", "Pass1234!")
    assertEquals("itest", user.displayName)
    assertTrue(tokens.accessToken.isNotBlank())
    assertTrue(tokens.refreshToken.isNotBlank())
    val refreshed = api.refreshToken(tokens.refreshToken)
    assertTrue(refreshed.accessToken.isNotBlank())
    val currentUser = api.fetchCurrentUser(tokens.accessToken)
    assertEquals("itest@example.com", currentUser.email)
    return tokens
  }

  private suspend fun testDeviceCreationAndUpdate(
          api: CosRayApi,
          httpClient: HttpClient,
          accessToken: String
  ): DeviceDto {
    val mac = uniqueMac()
    val targetDevice: DeviceDto =
            httpClient
                    .post("/api/devices/") {
                      header("Authorization", "Bearer $accessToken")
                      contentType(ContentType.Application.Json)
                      setBody(
                              """
            {
              "mac_address": "$mac",
              "name": "IntegrationDevice",
              "description": "app-integration-test"
            }
            """.trimIndent()
                      )
                    }
                    .body()
    val updated =
            api.updateDevice(
                    accessToken = accessToken,
                    deviceId = targetDevice.id,
                    name = "IntegrationDeviceUpdated",
                    isActive = true,
            )
    assertEquals("IntegrationDeviceUpdated", updated.name)
    return targetDevice.copy(macAddress = mac)
  }

  private suspend fun testMuonUpload(api: CosRayApi, accessToken: String, mac: String) {
    val muonResponse =
            api.uploadPacket(
                    accessToken,
                    PacketUploadRequest(
                            device = mac,
                            packetType = "muon",
                            muonPacket =
                                    MuonPacketDto(
                                            packageCounter = 100,
                                            utc = 1_710_001_111,
                                            head = listOf(0xAA, 0xBB, 0xCC),
                                            tail = listOf(0xDD, 0xEE, 0xFF),
                                            events =
                                                    listOf(
                                                            MuonEventDto(
                                                                    cpuTime = 1_001,
                                                                    energy = 321,
                                                                    pps = 99_001
                                                            )
                                                    ),
                                    ),
                    ),
            )
    assertEquals(1, muonResponse.recordsWritten)
  }

  private suspend fun testTimelineUpload(api: CosRayApi, accessToken: String, mac: String) {
    val timelineResponse =
            api.uploadPacket(
                    accessToken,
                    PacketUploadRequest(
                            device = mac,
                            packetType = "timeline",
                            timelinePacket =
                                    TimelinePacketDto(
                                            packageCounter = 101,
                                            head = listOf(0x12, 0x34, 0x56),
                                            tail = listOf(0x78, 0x9A, 0xBC),
                                            events =
                                                    listOf(
                                                            TimelineEventDto(
                                                                    cpuTime = 1,
                                                                    pps = 1,
                                                                    utc = 1_710_002_222,
                                                                    ppsUtc = 1,
                                                                    cputimePps = 1,
                                                                    gpsLong = 0,
                                                                    gpsLat = 0,
                                                                    gpsAlt = 0,
                                                                    accX = 0,
                                                                    accY = 0,
                                                                    accZ = 0,
                                                                    sipmTmp = 25,
                                                                    mcuTmp = 30,
                                                                    sipmImon = 40,
                                                                    sipmVmon = 50,
                                                            )
                                                    ),
                                    ),
                    ),
            )
    assertEquals(1, timelineResponse.recordsWritten)
  }

  private fun uniqueMac(): String {
    val suffix = (System.currentTimeMillis() % 256).toInt()
    return "AA:BB:CC:DD:EE:${"%02X".format(suffix)}"
  }
}
