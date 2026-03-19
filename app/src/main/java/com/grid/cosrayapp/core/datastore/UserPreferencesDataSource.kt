package com.grid.cosrayapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User
import com.grid.cosrayapp.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val USER_PREFERENCES_NAME = "cosray_user_preferences"

private val Context.dataStore: DataStore<Preferences> by
  preferencesDataStore(name = USER_PREFERENCES_NAME)

class UserPreferencesDataSource(context: Context) : AuthPreferences {
  private val store: DataStore<Preferences> = context.dataStore
  private val secureTokens = EncryptedTokenStore(context)

  @Suppress("ComplexCondition")
  override val authData: Flow<StoredAuthData?> =
    flow {
      migrateLegacyTokensIfNeeded()
      emitAll(
        store.data.map { preferences ->
          // token 必须从加密存储读取；若发现旧的明文 token，则尝试迁移。
          val legacyAccess = preferences[Keys.ACCESS_TOKEN]
          val legacyRefresh = preferences[Keys.REFRESH_TOKEN]
          val access = secureTokens.readAccessToken() ?: legacyAccess
          val refresh = secureTokens.readRefreshToken() ?: legacyRefresh
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
                    preferences[Keys.USER_ROLES]?.split(',')?.filter { it.isNotBlank() }
                      ?: emptyList(),
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
      )
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
      // token 不落盘到 DataStore；仅保留与用户相关的非敏感信息与过期时间。
      preferences.remove(Keys.ACCESS_TOKEN)
      preferences.remove(Keys.REFRESH_TOKEN)
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

    // 单独写入加密存储。
    secureTokens.writeTokens(tokens.accessToken, tokens.refreshToken)
  }

  override suspend fun clear() {
    store.edit { preferences -> preferences.clear() }
    secureTokens.clear()
  }

  /**
   * 将旧版本明文存储的 token 迁移到加密存储。
   *
   * 迁移失败策略：清空 token（强制重新登录），避免进入不一致状态。
   */
  private suspend fun migrateLegacyTokensIfNeeded() {
    var legacyAccess: String? = null
    var legacyRefresh: String? = null

    store.edit { preferences ->
      legacyAccess = preferences[Keys.ACCESS_TOKEN]
      legacyRefresh = preferences[Keys.REFRESH_TOKEN]
    }

    if (legacyAccess == null) return

    val existingSecureAccess = secureTokens.readAccessToken()
    if (existingSecureAccess != null) {
      store.edit { preferences ->
        preferences.remove(Keys.ACCESS_TOKEN)
        preferences.remove(Keys.REFRESH_TOKEN)
      }
      return
    }

    if (legacyRefresh == null) {
      secureTokens.clear()
    } else {
      try {
        secureTokens.writeTokens(legacyAccess!!, legacyRefresh!!)
      } catch (_: Throwable) {
        secureTokens.clear()
      }
    }

    store.edit { preferences ->
      preferences.remove(Keys.ACCESS_TOKEN)
      preferences.remove(Keys.REFRESH_TOKEN)
    }
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
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val OLED_DARK = booleanPreferencesKey("oled_dark")
  }
}

data class StoredAuthData(val user: User, val tokens: AuthTokens)
