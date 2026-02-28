package com.grid.cosrayapp.domain.model

import java.time.Duration
import java.time.Instant

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAt: Instant) {
  val isExpired: Boolean
    get() = Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_LEEWAY_SECONDS))

  val timeUntilExpiry: Duration
    get() = Duration.between(Instant.now(), expiresAt)

  companion object {
    private const val EXPIRY_LEEWAY_SECONDS = 90L
    private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 15L * 60L

    fun fromEpochMillis(
      accessToken: String,
      refreshToken: String,
      expiresAtMillis: Long,
    ): AuthTokens = AuthTokens(accessToken, refreshToken, Instant.ofEpochMilli(expiresAtMillis))

    fun fromJwtTokens(accessToken: String, refreshToken: String): AuthTokens =
      AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = Instant.now().plusSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS),
      )
  }
}
