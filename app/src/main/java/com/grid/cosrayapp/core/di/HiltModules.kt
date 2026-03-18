package com.grid.cosrayapp.core.di

import android.content.Context
import com.grid.cosrayapp.core.ble.BleController
import com.grid.cosrayapp.core.datastore.AuthPreferences
import com.grid.cosrayapp.core.datastore.UserPreferencesDataSource
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.HttpClientFactory
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.ble.BleRepository
import com.grid.cosrayapp.data.telemetry.TelemetryRepository
import com.grid.cosrayapp.data.telemetry.upload.DataStoreUploadQueue
import com.grid.cosrayapp.data.telemetry.upload.UploadQueue
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object HiltModules {
  @Provides
  @Singleton
  fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Provides @Singleton fun provideHttpClient(): HttpClient = HttpClientFactory.create()

  @Provides @Singleton fun provideCosRayApi(client: HttpClient): CosRayApi = CosRayApi(client)

  @Provides
  @Singleton
  fun provideJson(): Json =
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      encodeDefaults = true
      prettyPrint = false
    }

  @Provides
  @Singleton
  fun provideUserPreferencesDataSource(
    @ApplicationContext context: Context
  ): UserPreferencesDataSource = UserPreferencesDataSource(context)

  @Provides
  @Singleton
  fun provideAuthPreferences(userPreferences: UserPreferencesDataSource): AuthPreferences =
    userPreferences

  @Provides
  @Singleton
  fun provideBleController(
    @ApplicationContext context: Context,
    applicationScope: CoroutineScope,
  ): BleController = BleController(context, applicationScope)

  @Provides
  @Singleton
  fun provideAuthRepository(
    api: CosRayApi,
    userPreferences: AuthPreferences,
    applicationScope: CoroutineScope,
  ): AuthRepository =
    AuthRepository(api = api, userPreferences = userPreferences, externalScope = applicationScope)

  @Provides
  @Singleton
  fun provideBleRepository(controller: BleController): BleRepository = BleRepository(controller)

  @Provides
  @Singleton
  fun provideTelemetryRepository(
    api: CosRayApi,
    bleRepository: BleRepository,
    authRepository: AuthRepository,
    uploadQueue: UploadQueue,
    applicationScope: CoroutineScope,
  ): TelemetryRepository =
    TelemetryRepository(
      api = api,
      bleRepository = bleRepository,
      authRepository = authRepository,
      uploadQueue = uploadQueue,
      externalScope = applicationScope,
    )

  @Provides
  @Singleton
  fun provideUploadQueue(
    @ApplicationContext context: Context,
    json: Json,
  ): UploadQueue = DataStoreUploadQueue(context = context, json = json, maxSize = 10_000)
}
