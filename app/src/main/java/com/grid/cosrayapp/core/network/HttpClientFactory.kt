package com.grid.cosrayapp.core.network

import android.util.Log
import com.grid.cosrayapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner

object HttpClientFactory {
  private const val TAG = "CosRayHttp"
  private const val REQUEST_TIMEOUT_MS = 30_000L
  private const val CONNECT_TIMEOUT_MS = 10_000L
  private const val SOCKET_TIMEOUT_MS = 30_000L
  private const val MAX_RETRIES = 3

  fun create(): HttpClient =
    if (shouldEnablePinning()) {
      HttpClient(OkHttp) {
        expectSuccess = true

        install(HttpTimeout) {
          requestTimeoutMillis = REQUEST_TIMEOUT_MS
          connectTimeoutMillis = CONNECT_TIMEOUT_MS
          socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        install(HttpRequestRetry) {
          retryOnServerErrors(maxRetries = MAX_RETRIES)
          exponentialDelay()
        }

        install(ContentNegotiation) {
          json(
            Json {
              ignoreUnknownKeys = true
              isLenient = true
              encodeDefaults = true
              prettyPrint = false
            }
          )
        }

        install(Logging) {
          logger =
            object : Logger {
              override fun log(message: String) {
                if (BuildConfig.DEBUG) {
                  Log.d(TAG, message)
                }
              }
            }
          level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
        }

        engine { config { certificatePinner(buildCertificatePinner()) } }

        defaultRequest { url(NetworkConfig.baseUrl) }
      }
    } else {
      HttpClient(Android) {
      expectSuccess = true


      install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
      }

      install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = MAX_RETRIES)
        exponentialDelay()
      }

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
          }
        )
      }

      install(Logging) {
        logger =
          object : Logger {
            override fun log(message: String) {
              if (BuildConfig.DEBUG) {
                Log.d(TAG, message)
      }
    }
          }
        level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
      }

      defaultRequest { url(NetworkConfig.baseUrl) }
    }
    }

  /** Create HttpClient with custom base URL for API testing */
  fun createWithBaseUrl(baseUrl: String): HttpClient =
    HttpClient(Android) {
      expectSuccess = true

      install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
      }

      install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = MAX_RETRIES)
        exponentialDelay()
      }

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            prettyPrint = false
          }
        )
      }

      install(Logging) {
        logger =
          object : Logger {
            override fun log(message: String) {
              if (BuildConfig.DEBUG) {
                Log.d(TAG, message)
              }
            }
          }
        level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
      }

      defaultRequest { url(baseUrl) }
    }

  private fun shouldEnablePinning(): Boolean =
    !BuildConfig.DEBUG && BuildConfig.CERT_PINS.isNotEmpty()

  private fun buildCertificatePinner(): CertificatePinner {
    val host = NetworkConfig.baseUrlHost
    val pins = BuildConfig.CERT_PINS
    return CertificatePinner.Builder()
      .add(host, *pins)
      .add("*.$host", *pins)
      .build()
  }
}
