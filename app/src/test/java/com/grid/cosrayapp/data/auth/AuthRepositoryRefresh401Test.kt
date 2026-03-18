package com.grid.cosrayapp.data.auth

import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.datastore.AuthPreferences
import com.grid.cosrayapp.core.datastore.StoredAuthData
import com.grid.cosrayapp.core.datastore.UserPreferencesDataSource
import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.domain.model.AuthTokens
import com.grid.cosrayapp.domain.model.User
import com.grid.cosrayapp.domain.model.UserId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryRefresh401Test {
  @Test
  fun `refresh 401 should clear auth state`() = runTest {
    val prefs = FakeAuthPreferences()
    val expiredTokens =
      AuthTokens.fromEpochMillis(
        accessToken = "a.b.c",
        refreshToken = "refresh",
        expiresAtMillis = Instant.EPOCH.toEpochMilli(),
      )
    val user = User(id = UserId("1"), email = "u@example.com", displayName = "u")
    prefs.persistAuth(user, expiredTokens)

    val client =
      HttpClient(
        MockEngine {
          respondError(HttpStatusCode.Unauthorized)
        }
      ) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      }
    val api = CosRayApi(client)

    val repository =
      AuthRepository(
        api = api,
        userPreferences = prefs,
        externalScope = backgroundScope,
      )

    runCurrent()

    val tokenResult = repository.ensureValidToken()
    assertTrue(tokenResult is CosRayResult.Error)

    assertNull(repository.tokens.value)
    assertEquals(AuthState.Unauthenticated, repository.authState.value)
    assertNull(prefs.latestAuthData())
  }
}

private class FakeAuthPreferences : AuthPreferences {
  private val state = MutableStateFlow<StoredAuthData?>(null)

  override val authData: Flow<StoredAuthData?> = state

  override suspend fun persistAuth(user: User, tokens: AuthTokens) {
    state.value = StoredAuthData(user = user, tokens = tokens)
  }

  override suspend fun clear() {
    state.value = null
  }

  fun latestAuthData(): StoredAuthData? = state.value
}
