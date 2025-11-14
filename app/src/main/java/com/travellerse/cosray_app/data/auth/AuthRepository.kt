package com.travellerse.cosray_app.data.auth

import com.travellerse.cosray_app.core.common.CosRayResult
import com.travellerse.cosray_app.core.common.runCosRayCatching
import com.travellerse.cosray_app.core.datastore.StoredAuthData
import com.travellerse.cosray_app.core.datastore.UserPreferencesDataSource
import com.travellerse.cosray_app.core.network.CosRayApi
import com.travellerse.cosray_app.domain.model.AuthTokens
import com.travellerse.cosray_app.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthRepository(
        private val api: CosRayApi,
        private val userPreferences: UserPreferencesDataSource,
        externalScope: CoroutineScope
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _tokens = MutableStateFlow<AuthTokens?>(null)
    val tokens: StateFlow<AuthTokens?> = _tokens.asStateFlow()

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

    suspend fun register(email: String, password: String, displayName: String): CosRayResult<Unit> =
            runCosRayCatching {
                val (user, tokens) = api.register(email, password, displayName)
                persistAuth(user, tokens)
            }

    suspend fun refreshTokens(): CosRayResult<Unit> {
        val currentTokens = _tokens.value
        val currentUser = (authState.value as? AuthState.Authenticated)?.user
        return if (currentTokens == null || currentUser == null) {
            CosRayResult.Error(IllegalStateException("Missing authentication context"))
        } else if (!currentTokens.isExpired) {
            CosRayResult.Success(Unit)
        } else {
            runCosRayCatching {
                val refreshed = api.refreshToken(currentTokens.refreshToken)
                persistAuth(currentUser, refreshed)
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
}
