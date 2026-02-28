package com.grid.cosrayapp.data.auth

import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.common.runCosRayCatching
import com.grid.cosrayapp.core.datastore.StoredAuthData
import com.grid.cosrayapp.core.datastore.UserPreferencesDataSource
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(
  private val api: CosRayApi,
  private val userPreferences: UserPreferencesDataSource,
  externalScope: CoroutineScope,
) {
  private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  private val _tokens = MutableStateFlow<AuthTokens?>(null)
  val tokens: StateFlow<AuthTokens?> = _tokens.asStateFlow()

  private val refreshMutex = Mutex()

  init {
    externalScope.launch {
      userPreferences.authData.collect { stored: StoredAuthData? ->
        if (stored == null) {
          _tokens.value = null
          _authState.value = AuthState.Unauthenticated
        } else {
          _tokens.value = stored.tokens
          _authState.value = AuthState.Authenticated(stored.user)
        }
      }
    }
  }

  suspend fun login(email: String, password: String): CosRayResult<Unit> = runCosRayCatching {
    val (user, tokens) = api.login(email, password)
    persistAuth(user, tokens)
  }

  /** Refresh authentication tokens with mutex protection to prevent concurrent refresh attempts */
  suspend fun refreshTokens(): CosRayResult<Unit> =
    refreshMutex.withLock {
      val currentTokens = _tokens.value
      val currentUser = (authState.value as? AuthState.Authenticated)?.user
      return if (currentTokens == null || currentUser == null) {
        CosRayResult.Error(IllegalStateException("Missing authentication context"))
      } else if (!currentTokens.isExpired) {
        CosRayResult.Success(Unit)
      } else {
        refreshTokensLocked(currentUser, currentTokens)
      }
    }

  /** Ensure we have a valid access token, refreshing if necessary */
  suspend fun ensureValidToken(): CosRayResult<String> =
    refreshMutex.withLock {
      val current =
        _tokens.value ?: return CosRayResult.Error(IllegalStateException("Missing access token"))

      if (!current.isExpired) {
        return CosRayResult.Success(current.accessToken)
      }

      val currentUser =
        (authState.value as? AuthState.Authenticated)?.user
          ?: return CosRayResult.Error(IllegalStateException("Missing authentication context"))

      refreshTokensLocked(currentUser, current).let { result ->
        when (result) {
          is CosRayResult.Success -> {
            _tokens.value?.accessToken?.let { CosRayResult.Success(it) }
              ?: CosRayResult.Error(
                IllegalStateException("Token refresh succeeded but token is null")
              )
          }

          is CosRayResult.Error -> {
            result
          }
        }
      }
    }

  suspend fun fetchCurrentUser(): CosRayResult<User> {
    val currentTokens = _tokens.value
    return if (currentTokens == null) {
      CosRayResult.Error(IllegalStateException("Missing access token"))
    } else {
      runCosRayCatching { api.fetchCurrentUser(currentTokens.accessToken) }
    }
  }

  suspend fun logout(): CosRayResult<Unit> = runCosRayCatching {
    userPreferences.clear()
    _tokens.value = null
  }

  private suspend fun persistAuth(user: User, tokens: AuthTokens) {
    userPreferences.persistAuth(user, tokens)
    _tokens.value = tokens
  }

  private suspend fun refreshTokensLocked(user: User, tokens: AuthTokens): CosRayResult<Unit> =
    runCosRayCatching {
      val refreshed = api.refreshToken(tokens.refreshToken)
      persistAuth(user, refreshed)
    }
}
