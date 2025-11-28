package com.grid.cosrayapp.data.auth

import com.grid.cosrayapp.domain.model.User

sealed interface AuthState {
  data object Loading : AuthState

  data object Unauthenticated : AuthState

  data class Authenticated(val user: User) : AuthState
}
