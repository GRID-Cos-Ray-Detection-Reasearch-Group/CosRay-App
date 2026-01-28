package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User

/**
 * API interface for authentication operations.
 *
 * Handles user login, registration, token refresh, and session management.
 */
interface AuthApi {
  /**
   * Login with username and password.
   *
   * @param username Username or email.
   * @param password User password.
   * @return User info and authentication tokens on success.
   */
  suspend fun login(username: String, password: String): ApiResult<Pair<User, AuthTokens>>

  /**
   * Register a new user account.
   *
   * @param email User email address.
   * @param password User password.
   * @param displayName User display name.
   * @return User info and authentication tokens on success.
   */
  suspend fun register(
    email: String,
    password: String,
    displayName: String,
  ): ApiResult<Pair<User, AuthTokens>>

  /**
   * Refresh authentication tokens.
   *
   * @param refreshToken Current refresh token.
   * @return New authentication tokens.
   */
  suspend fun refreshToken(refreshToken: String): ApiResult<AuthTokens>

  /**
   * Fetch the currently authenticated user's profile.
   *
   * @param accessToken Current access token.
   * @return User profile information.
   */
  suspend fun fetchCurrentUser(accessToken: String): ApiResult<User>

  /**
   * Logout and invalidate the current session.
   *
   * @param accessToken Current access token.
   * @return Unit on success.
   */
  suspend fun logout(accessToken: String): ApiResult<Unit>
}
