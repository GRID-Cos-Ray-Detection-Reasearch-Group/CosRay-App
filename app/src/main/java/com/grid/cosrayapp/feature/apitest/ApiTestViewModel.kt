@file:Suppress("MagicNumber")

package com.grid.cosrayapp.feature.apitest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.HttpClientFactory
import com.grid.cosrayapp.core.network.model.MuonEventDto
import com.grid.cosrayapp.core.network.model.MuonPacketDto
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.TimelineEventDto
import com.grid.cosrayapp.core.network.model.TimelinePacketDto
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class ApiTestUiState(
  val baseUrl: String = "http://10.0.2.2:8000",
  val token: String = "",
  val username: String = "test",
  val password: String = "LocalPass123!",
  val macAddress: String = "00:11:22:33:44:55",
  val deviceName: String = "Local Device",
  val deviceDescription: String = "Local Device Description",
  val deviceId: String = "1",
  val response: String = "",
  val isLoading: Boolean = false,
  val error: String? = null,
)

@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
@HiltViewModel
class ApiTestViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(ApiTestUiState())
  val uiState: StateFlow<ApiTestUiState> = _uiState.asStateFlow()

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }

  private var customHttpClient: HttpClient? = null
  private var customApi: CosRayApi? = null

  fun updateBaseUrl(url: String) {
    _uiState.update { it.copy(baseUrl = url) }
    recreateApiClient()
  }

  fun updateToken(token: String) {
    _uiState.update { it.copy(token = token) }
  }

  fun updateUsername(username: String) {
    _uiState.update { it.copy(username = username) }
  }

  fun updatePassword(password: String) {
    _uiState.update { it.copy(password = password) }
  }

  fun updateMacAddress(macAddress: String) {
    _uiState.update { it.copy(macAddress = macAddress) }
  }

  fun updateDeviceName(name: String) {
    _uiState.update { it.copy(deviceName = name) }
  }

  fun updateDeviceDescription(description: String) {
    _uiState.update { it.copy(deviceDescription = description) }
  }

  fun updateDeviceId(id: String) {
    _uiState.update { it.copy(deviceId = id) }
  }

  fun clearResponse() {
    _uiState.update { it.copy(response = "", error = null) }
  }

  private fun recreateApiClient() {
    customHttpClient?.close()
    customHttpClient = HttpClientFactory.createWithBaseUrl(_uiState.value.baseUrl)
    customApi = CosRayApi(customHttpClient!!)
  }

  private fun getApi(): CosRayApi {
    if (customApi == null) {
      recreateApiClient()
    }
    return customApi!!
  }

  fun testLogin() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val (user, tokens) = getApi().login(state.username, state.password)
        val responseText = buildString {
          appendLine("{")
          appendLine("  \"user\": {")
          appendLine("    \"id\": \"${user.id.value}\",")
          appendLine("    \"email\": \"${user.email}\",")
          appendLine("    \"displayName\": \"${user.displayName}\"")
          appendLine("  },")
          appendLine("  \"tokens\": {")
          appendLine("    \"accessToken\": \"${tokens.accessToken}\",")
          appendLine("    \"refreshToken\": \"${tokens.refreshToken}\",")
          appendLine("    \"expiresAt\": ${tokens.expiresAt}")
          appendLine("  }")
          appendLine("}")
        }
        _uiState.update {
          it.copy(response = responseText, token = tokens.accessToken, isLoading = false)
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testGetUserInfo() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val user = getApi().fetchCurrentUser(state.token)
        val responseText = buildString {
          appendLine("{")
          appendLine("  \"id\": \"${user.id.value}\",")
          appendLine("  \"email\": \"${user.email}\",")
          appendLine("  \"displayName\": \"${user.displayName}\",")
          appendLine("  \"avatarUrl\": \"${user.avatarUrl}\",")
          appendLine("  \"organization\": \"${user.organization}\",")
          appendLine("  \"roles\": ${user.roles}")
          appendLine("}")
        }
        _uiState.update { it.copy(response = responseText, isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testListDevices() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val devices = getApi().getDevices(state.token)
        _uiState.update { it.copy(response = json.encodeToString(devices), isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testGetDevice() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val deviceId =
          state.deviceId.toIntOrNull() ?: throw IllegalArgumentException("Invalid device ID")
        val device = getApi().getDevice(state.token, deviceId)
        _uiState.update { it.copy(response = json.encodeToString(device), isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testUpdateDevice() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val deviceId =
          state.deviceId.toIntOrNull() ?: throw IllegalArgumentException("Invalid device ID")
        val device =
          getApi()
            .updateDevice(
              accessToken = state.token,
              deviceId = deviceId,
              name = state.deviceName.ifBlank { null },
              description = state.deviceDescription.ifBlank { null },
            )
        _uiState.update { it.copy(response = json.encodeToString(device), isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testDeleteDevice() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val deviceId =
          state.deviceId.toIntOrNull() ?: throw IllegalArgumentException("Invalid device ID")
        getApi().deleteDevice(state.token, deviceId)
        _uiState.update { it.copy(response = "Device deleted successfully", isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testUploadMuonPacket() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        // Create sample muon packet with all required fields
        val muonPacket =
          MuonPacketDto(
            packageCounter = 1,
            utc = System.currentTimeMillis() / 1000,
            events =
              listOf(
                MuonEventDto(
                  cpuTime = 1000,
                  energy = 150,
                  pps = 100,
                  timestamp = System.currentTimeMillis(),
                )
              ),
            head = listOf(0xAA, 0xBB, 0xCC),
            tail = listOf(0xDD, 0xEE, 0xFF),
            crc = 0x1234, // Example CRC
            reserved = listOf(0, 0, 0, 0, 0, 0),
          )
        val request =
          PacketUploadRequest(
            device = state.macAddress,
            packetType = "muon",
            muonPacket = muonPacket,
          )
        val response = getApi().uploadPacket(state.token, request)
        _uiState.update { it.copy(response = json.encodeToString(response), isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  fun testUploadTimelinePacket() {
    val state = _uiState.value
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        // Create sample timeline packet with all required fields
        val timelinePacket =
          TimelinePacketDto(
            packageCounter = 1,
            events =
              listOf(
                TimelineEventDto(
                  cpuTime = 1000,
                  pps = 100,
                  utc = System.currentTimeMillis() / 1000,
                  ppsUtc = 100,
                  cputimePps = 1000,
                  gpsLong = 0,
                  gpsLat = 0,
                  gpsAlt = 0,
                  accX = 0,
                  accY = 0,
                  accZ = 0,
                  sipmTmp = 25,
                  mcuTmp = 30,
                  sipmImon = 0,
                  sipmVmon = 0,
                  timestamp = System.currentTimeMillis(),
                )
              ),
            head = listOf(0x12, 0x34, 0x56),
            tail = listOf(0x78, 0x9A, 0xBC),
            crc = 0x5678, // Example CRC
            reserved = List(20) { 0 },
          )
        val request =
          PacketUploadRequest(
            device = state.macAddress,
            packetType = "timeline",
            timelinePacket = timelinePacket,
          )
        val response = getApi().uploadPacket(state.token, request)
        _uiState.update { it.copy(response = json.encodeToString(response), isLoading = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            error = e.message ?: "Unknown error",
            response = "Error: ${e.message}",
            isLoading = false,
          )
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    customHttpClient?.close()
  }
}
