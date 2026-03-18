package com.grid.cosrayapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User
import com.grid.cosrayapp.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val USER_PREFERENCES_NAME = "cosray_user_preferences"

private val Context.dataStore: DataStore<Preferences> by
  preferencesDataStore(name = USER_PREFERENCES_NAME)

class UserPreferencesDataSource(context: Context) : AuthPreferences {
  private val store: DataStore<Preferences> = context.dataStore

  @Suppress("ComplexCondition")
  override val authData: Flow<StoredAuthData?> =
    store.data.map { preferences ->
      val access = preferences[Keys.ACCESS_TOKEN]
      val refresh = preferences[Keys.REFRESH_TOKEN]
      val expires = preferences[Keys.EXPIRES_AT]
      val userId = preferences[Keys.USER_ID]
      val email = preferences[Keys.USER_EMAIL]
      val name = preferences[Keys.USER_NAME]
      if (
        access != null &&
          refresh != null &&
          expires != null &&
          userId != null &&
          email != null &&
          name != null
      ) {
        StoredAuthData(
          user =
            User(
              id = UserId(userId),
              email = email,
              displayName = name,
              avatarUrl = preferences[Keys.USER_AVATAR],
              organization = preferences[Keys.USER_ORGANIZATION],
              roles =
                preferences[Keys.USER_ROLES]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            ),
          tokens =
            AuthTokens.fromEpochMillis(
              accessToken = access,
              refreshToken = refresh,
              expiresAtMillis = expires,
            ),
        )
      } else {
        null
      }
    }

  val darkTheme: Flow<Boolean?> = store.data.map { preferences -> preferences[Keys.DARK_THEME] }
  val oledDark: Flow<Boolean?> = store.data.map { preferences -> preferences[Keys.OLED_DARK] }

  suspend fun setDarkTheme(enabled: Boolean) {
    store.edit { preferences -> preferences[Keys.DARK_THEME] = enabled }
  }

  suspend fun setOledDark(enabled: Boolean) {
    store.edit { preferences -> preferences[Keys.OLED_DARK] = enabled }
  }

  override suspend fun persistAuth(user: User, tokens: AuthTokens) {
    store.edit { preferences ->
      preferences[Keys.ACCESS_TOKEN] = tokens.accessToken
      preferences[Keys.REFRESH_TOKEN] = tokens.refreshToken
      preferences[Keys.EXPIRES_AT] = tokens.expiresAt.toEpochMilli()
      preferences[Keys.USER_ID] = user.id.value
      preferences[Keys.USER_EMAIL] = user.email
      preferences[Keys.USER_NAME] = user.displayName
      user.avatarUrl?.let { preferences[Keys.USER_AVATAR] = it }
        ?: preferences.remove(Keys.USER_AVATAR)
      user.organization?.let { preferences[Keys.USER_ORGANIZATION] = it }
        ?: preferences.remove(Keys.USER_ORGANIZATION)
      if (user.roles.isNotEmpty()) {
        preferences[Keys.USER_ROLES] = user.roles.joinToString(",")
      } else {
        preferences.remove(Keys.USER_ROLES)
      }
    }
  }

  override suspend fun clear() {
    store.edit { preferences -> preferences.clear() }
  }

  private object Keys {
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    val EXPIRES_AT = longPreferencesKey("expires_at")
    val USER_ID = stringPreferencesKey("user_id")
    val USER_EMAIL = stringPreferencesKey("user_email")
    val USER_NAME = stringPreferencesKey("user_name")
    val USER_AVATAR = stringPreferencesKey("user_avatar")
    val USER_ORGANIZATION = stringPreferencesKey("user_org")
    val USER_ROLES = stringPreferencesKey("user_roles")
    val DARK_THEME = androidx.datastore.preferences.core.booleanPreferencesKey("dark_theme")
    val OLED_DARK = androidx.datastore.preferences.core.booleanPreferencesKey("oled_dark")
  }
}

data class StoredAuthData(val user: User, val tokens: AuthTokens)
