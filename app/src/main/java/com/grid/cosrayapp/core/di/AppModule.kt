package com.grid.cosrayapp.core.di

import android.content.Context
import com.grid.cosrayapp.core.ble.BleController
import com.grid.cosrayapp.core.ble.BleScanner
import com.grid.cosrayapp.core.ble.BleScannerImpl
import com.grid.cosrayapp.core.datastore.TokenStore
import com.grid.cosrayapp.core.network.AuthApi
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.DeviceApi
import com.grid.cosrayapp.core.network.HttpClientFactory
import com.grid.cosrayapp.core.network.TelemetryApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-level dependency injection module.
 *
 * Provides singleton instances of core application dependencies. In a production app, consider
 * using a DI framework like Hilt or Koin.
 */
object AppModule {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var _context: Context? = null
  private var _httpClient: HttpClient? = null
  private var _cosRayApi: CosRayApi? = null
  private var _bleController: BleController? = null
  private var _bleScanner: BleScanner? = null
  private var _tokenStore: TokenStore? = null

  /** Initialize the module with application context. Must be called from Application.onCreate(). */
  fun initialize(context: Context) {
    _context = context.applicationContext
  }

  /** Provide the application context. */
  fun provideContext(): Context =
    _context ?: throw IllegalStateException("AppModule not initialized. Call initialize() first.")

  /** Provide the application-scoped CoroutineScope. */
  fun provideApplicationScope(): CoroutineScope = applicationScope

  /** Provide the HTTP client for network requests. */
  fun provideHttpClient(): HttpClient {
    if (_httpClient == null) {
      _httpClient = HttpClientFactory.create()
    }
    return _httpClient!!
  }

  /** Provide the TokenStore for managing authentication tokens. */
  fun provideTokenStore(): TokenStore {
    if (_tokenStore == null) {
      _tokenStore = TokenStore(provideContext())
    }
    return _tokenStore!!
  }

  /** Provide the CosRayApi instance. */
  fun provideCosRayApi(): CosRayApi {
    if (_cosRayApi == null) {
      _cosRayApi = CosRayApi(provideHttpClient())
    }
    return _cosRayApi!!
  }

  /** Provide the BleController instance. */
  fun provideBleController(): BleController {
    if (_bleController == null) {
      _bleController = BleController(provideContext(), applicationScope)
    }
    return _bleController!!
  }

  /** Provide the BleScanner instance. */
  fun provideBleScanner(): BleScanner {
    if (_bleScanner == null) {
      _bleScanner = BleScannerImpl(provideContext(), applicationScope)
    }
    return _bleScanner!!
  }

  /**
   * Provide the AuthApi interface.
   *
   * Currently delegates to CosRayApi implementation. In the future, this could be a standalone
   * implementation.
   */
  fun provideAuthApi(): AuthApi {
    return AuthApiAdapter(provideCosRayApi())
  }

  /**
   * Provide the DeviceApi interface.
   *
   * Currently delegates to CosRayApi implementation.
   */
  fun provideDeviceApi(): DeviceApi {
    return DeviceApiAdapter(provideCosRayApi())
  }

  /**
   * Provide the TelemetryApi interface.
   *
   * Currently delegates to CosRayApi implementation.
   */
  fun provideTelemetryApi(): TelemetryApi {
    return TelemetryApiAdapter(provideCosRayApi())
  }

  /** Clear all cached instances. Useful for testing or logout scenarios. */
  fun reset() {
    _httpClient?.close()
    _httpClient = null
    _cosRayApi = null
    _bleController?.shutdown()
    _bleController = null
    _bleScanner = null
    _tokenStore = null
  }
}

/** Adapter to bridge CosRayApi to AuthApi interface. */
private class AuthApiAdapter(private val api: CosRayApi) : AuthApi {
  override suspend fun login(username: String, password: String) =
    com.grid.cosrayapp.core.network.apiResultOf { api.login(username, password) }

  override suspend fun register(email: String, password: String, displayName: String) =
    com.grid.cosrayapp.core.network.apiResultOf { api.register(email, password, displayName) }

  override suspend fun refreshToken(refreshToken: String) =
    com.grid.cosrayapp.core.network.apiResultOf { api.refreshToken(refreshToken) }

  override suspend fun fetchCurrentUser(accessToken: String) =
    com.grid.cosrayapp.core.network.apiResultOf { api.fetchCurrentUser(accessToken) }

  override suspend fun logout(
    accessToken: String
  ): com.grid.cosrayapp.core.network.ApiResult<Unit> {
    // TODO: Implement when CosRayApi adds logout endpoint
    return com.grid.cosrayapp.core.network.ApiResult.Error(
      code = 501,
      message = "Logout endpoint not implemented in backend yet",
    )
  }
}

/** Adapter to bridge CosRayApi to DeviceApi interface. */
private class DeviceApiAdapter(private val api: CosRayApi) : DeviceApi {
  override suspend fun registerDevice(
    accessToken: String,
    request: com.grid.cosrayapp.core.network.RegisterDeviceRequest,
  ) =
    com.grid.cosrayapp.core.network.apiResultOf {
      api.registerDevice(accessToken, request.macAddress, request.name, request.description)
    }

  override suspend fun getDevices(accessToken: String) =
    com.grid.cosrayapp.core.network.apiResultOf { api.getDevices(accessToken) }

  override suspend fun getDevice(accessToken: String, deviceId: Int) =
    com.grid.cosrayapp.core.network.apiResultOf { api.getDevice(accessToken, deviceId) }

  override suspend fun updateDevice(
    accessToken: String,
    deviceId: Int,
    request: com.grid.cosrayapp.core.network.UpdateDeviceRequest,
  ) =
    com.grid.cosrayapp.core.network.apiResultOf {
      api.updateDevice(accessToken, deviceId, request.name, request.description, request.isActive)
    }

  override suspend fun deleteDevice(accessToken: String, deviceId: Int) =
    com.grid.cosrayapp.core.network.apiResultOf { api.deleteDevice(accessToken, deviceId) }
}

/** Adapter to bridge CosRayApi to TelemetryApi interface. */
private class TelemetryApiAdapter(private val api: CosRayApi) : TelemetryApi {
  override suspend fun uploadPacket(
    accessToken: String,
    request: com.grid.cosrayapp.core.network.model.PacketUploadRequest,
  ) = com.grid.cosrayapp.core.network.apiResultOf { api.uploadPacket(accessToken, request) }

  override suspend fun uploadBatch(
    accessToken: String,
    requests: List<com.grid.cosrayapp.core.network.model.PacketUploadRequest>,
  ) =
    com.grid.cosrayapp.core.network.apiResultOf {
      var successCount = 0
      val errors = mutableListOf<String>()

      requests.forEach { request ->
        try {
          api.uploadPacket(accessToken, request)
          successCount++
        } catch (e: Exception) {
          errors.add(e.message ?: "Unknown error")
        }
      }

      com.grid.cosrayapp.core.network.BatchUploadResponse(
        successCount = successCount,
        failedCount = requests.size - successCount,
        errors = errors,
      )
    }
}
