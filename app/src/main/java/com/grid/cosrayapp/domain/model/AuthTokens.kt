package com.grid.cosrayapp.domain.model

import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAt: Instant) {
  val isExpired: Boolean
    get() = Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_LEEWAY_SECONDS))

  val timeUntilExpiry: Duration
    get() = Duration.between(Instant.now(), expiresAt)

  companion object {
    private const val EXPIRY_LEEWAY_SECONDS = 90L
    private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 15L * 60L
    private const val JWT_PARTS_COUNT = 3

    fun fromEpochMillis(
      accessToken: String,
      refreshToken: String,
      expiresAtMillis: Long,
    ): AuthTokens = AuthTokens(accessToken, refreshToken, Instant.ofEpochMilli(expiresAtMillis))

    fun fromJwtTokens(accessToken: String, refreshToken: String): AuthTokens {
      val defaultExpiry = Instant.now().plusSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS)
      val expiresAt =
        try {
          val parts = accessToken.split(".")
          if (parts.size == JWT_PARTS_COUNT) {
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val json = Json.parseToJsonElement(payload).jsonObject
            val exp = json["exp"]?.jsonPrimitive?.longOrNull
            if (exp != null) {
              Instant.ofEpochSecond(exp)
            } else {
              defaultExpiry
            }
          } else {
            defaultExpiry
          }
        } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
          defaultExpiry
        }

      return AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
      )
    }
  }
}
