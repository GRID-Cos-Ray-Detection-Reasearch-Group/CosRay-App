package com.grid.cosrayapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val userPreferences: com.grid.cosrayapp.core.datastore.UserPreferencesDataSource,
) : ViewModel() {

  val uiState: StateFlow<SettingsUiState> =
    kotlinx.coroutines.flow.combine(
        authRepository.authState,
        userPreferences.darkTheme,
        userPreferences.oledDark,
      ) { authState, darkTheme, oledDark ->
        SettingsUiState(
          user = (authState as? AuthState.Authenticated)?.user,
          isAuthenticated = authState is AuthState.Authenticated,
          isDarkThemeOn = darkTheme ?: false,
          isOledDarkOn = oledDark ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
      )

  fun logout() {
    viewModelScope.launch { authRepository.logout() }
  }

  fun toggleDarkTheme(enabled: Boolean) {
    viewModelScope.launch { userPreferences.setDarkTheme(enabled) }
  }

  fun toggleOledDark(enabled: Boolean) {
    viewModelScope.launch { userPreferences.setOledDark(enabled) }
  }
}

data class SettingsUiState(
  val user: User? = null,
  val isAuthenticated: Boolean = false,
  val isDarkThemeOn: Boolean = false,
  val isOledDarkOn: Boolean = false,
  val areNotificationsEnabled: Boolean = true, // Placeholder
)
