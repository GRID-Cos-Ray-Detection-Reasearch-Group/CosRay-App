package com.travellerse.cosray_app.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(
        val email: String,
        val password: String,
        @SerialName("display_name") val displayName: String
)

@Serializable data class AuthResponse(val data: AuthData, val meta: AuthMeta, val status: Int)

@Serializable
data class AuthData(val user: UserResponse, val methods: List<AuthMethod> = emptyList())

@Serializable
data class AuthMethod(val method: String, val username: String, val at: Double? = null)

@Serializable
data class AuthMeta(
        @SerialName("is_authenticated") val isAuthenticated: Boolean,
        @SerialName("session_token") val sessionToken: String,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("csrf_token") val csrfToken: String? = null
)

@Serializable
data class UserResponse(
        val id: String,
        val username: String,
        val email: String,
        val display: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val organization: String? = null,
        val roles: List<String> = emptyList()
)
