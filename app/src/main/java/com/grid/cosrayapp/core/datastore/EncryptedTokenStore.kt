package com.grid.cosrayapp.core.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 用于存储 access/refresh token 的加密存储。
 *
 * 说明：
 * - Token 属于高敏感信息，必须避免明文落盘。
 * - 这里使用 AndroidX Security Crypto 基于 Keystore 的 AES256-GCM 加密。
 */
class EncryptedTokenStore(context: Context) {
  private val masterKey: MasterKey =
    MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()

  private val prefs =
    EncryptedSharedPreferences.create(
      context,
      FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  fun readAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

  fun readRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

  fun writeTokens(accessToken: String, refreshToken: String) {
    prefs
      .edit()
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .putString(KEY_REFRESH_TOKEN, refreshToken)
      .apply()
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  private companion object {
    private const val FILE_NAME = "cosray_secure_tokens"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
  }
}
