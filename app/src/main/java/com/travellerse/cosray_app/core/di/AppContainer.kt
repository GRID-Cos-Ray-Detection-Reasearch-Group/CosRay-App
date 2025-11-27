package com.travellerse.cosray_app.core.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.travellerse.cosray_app.core.ble.BleController
import com.travellerse.cosray_app.core.datastore.UserPreferencesDataSource
import com.travellerse.cosray_app.core.network.CosRayApi
import com.travellerse.cosray_app.core.network.HttpClientFactory
import com.travellerse.cosray_app.data.auth.AuthRepository
import com.travellerse.cosray_app.data.ble.BleRepository
import com.travellerse.cosray_app.data.telemetry.TelemetryRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

val LocalAppContainer =
        staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

interface AppContainer : AutoCloseable {
    val authRepository: AuthRepository
    val bleRepository: BleRepository
    val telemetryRepository: TelemetryRepository
    val api: CosRayApi
    val httpClient: HttpClient
    val applicationScope: CoroutineScope
}

class AppContainerImpl(appContext: Context, externalScope: CoroutineScope) : AppContainer {

    override val applicationScope: CoroutineScope = externalScope
    override val httpClient: HttpClient = HttpClientFactory.create()

    private val userPreferences = UserPreferencesDataSource(appContext)
    private val bleController = BleController(appContext, externalScope)

    override val api: CosRayApi = CosRayApi(httpClient)

    override val authRepository: AuthRepository by lazy {
        AuthRepository(
            api = api,
            userPreferences = userPreferences,
            externalScope = externalScope
        )
    }

    override val bleRepository: BleRepository by lazy {
        BleRepository(bleController)
    }

    override val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository(
            api = api,
            bleRepository = bleRepository,
            authRepository = authRepository,
            externalScope = externalScope
        )
    }

    override fun close() {
        // Clean up resources in reverse order of initialization
        bleController.shutdown()
        httpClient.close()
    }
}
