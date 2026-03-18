package com.grid.cosrayapp.core.datastore

import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthPreferences {
  val authData: Flow<StoredAuthData?>

  suspend fun persistAuth(user: User, tokens: AuthTokens)

  suspend fun clear()
}
