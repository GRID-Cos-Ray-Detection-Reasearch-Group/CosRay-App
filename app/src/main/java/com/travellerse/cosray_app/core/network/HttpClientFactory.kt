package com.travellerse.cosray_app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun create(): HttpClient =
            HttpClient(Android) {
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

                install(Logging) {
                    logger =
                            object : Logger {
                                override fun log(message: String) {
                                    // Replace with Timber or Logcat when available
                                }
                            }
                    level = LogLevel.INFO
                }

                defaultRequest { url(NetworkConfig.baseUrl) }
            }
}
