package com.travellerse.cosray_app.domain.model

import java.time.Duration
import java.time.Instant

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAt: Instant) {

    val isExpired: Boolean
        get() = Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_LEEWAY_SECONDS))

    val timeUntilExpiry: Duration
        get() = Duration.between(Instant.now(), expiresAt)

    companion object {
        private const val EXPIRY_LEEWAY_SECONDS = 90L
        private val INFINITE_EXPIRY: Instant = Instant.ofEpochMilli(Long.MAX_VALUE / 2)

        fun fromEpochMillis(
                accessToken: String,
                refreshToken: String,
                expiresAtMillis: Long
        ): AuthTokens = AuthTokens(accessToken, refreshToken, Instant.ofEpochMilli(expiresAtMillis))

        fun fromSessionToken(sessionToken: String): AuthTokens =
                AuthTokens(sessionToken, sessionToken, INFINITE_EXPIRY)
    }
}
