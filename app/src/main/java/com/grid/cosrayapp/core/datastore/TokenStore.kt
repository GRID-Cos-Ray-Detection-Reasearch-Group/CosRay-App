package com.grid.cosrayapp.core.datastore

import android.content.Context
import com.grid.cosrayapp.domain.model.AuthTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * TokenStore provides a simple interface for managing authentication tokens.
 *
 * Wraps UserPreferencesDataSource to provide a focused API for token operations
 * including retrieving, storing, and clearing auth tokens.
 */
class TokenStore(context: Context) {
  private val dataSource = UserPreferencesDataSource(context)

  /** Flow that emits the current access token, or null if not authenticated */
  val accessToken: Flow<String?> =
    dataSource.authData.map { it?.tokens?.accessToken }

  /** Flow that emits the current refresh token, or null if not authenticated */
  val refreshToken: Flow<String?> =
    dataSource.authData.map { it?.tokens?.refreshToken }

  /** Flow that emits whether the user is currently authenticated */
  val isAuthenticated: Flow<Boolean> =
    dataSource.authData.map { it != null }

  /**
   * Save authentication tokens.
   *
   * @param user The user associated with these tokens
   * @param tokens The authentication tokens to store
   */
  suspend fun saveTokens(user: com.grid.cosrayapp.domain.model.User, tokens: AuthTokens) {
    dataSource.persistAuth(user, tokens)
  }

  /** Clear all stored authentication data */
  suspend fun clear() {
    dataSource.clear()
  }
}
