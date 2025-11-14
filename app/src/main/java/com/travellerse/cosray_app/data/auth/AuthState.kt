package com.travellerse.cosray_app.data.auth

import com.travellerse.cosray_app.domain.model.User

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
}
