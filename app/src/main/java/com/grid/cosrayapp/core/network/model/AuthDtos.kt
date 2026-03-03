package com.grid.cosrayapp.core.network.model

import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable data class AuthResponse(val refresh: String, val access: String)

@Serializable
data class RegisterResponse(val refresh: String, val access: String, val user: UserResponse)

@Serializable data class TokenRefreshRequest(val refresh: String)

@Serializable data class TokenResponse(val access: String)

@Serializable data class UserResponse(val id: Int, val username: String, val email: String)
