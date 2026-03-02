package com.grid.cosrayapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthState
import com.grid.cosrayapp.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
) : ViewModel() {
  
  val uiState: StateFlow<SettingsUiState> = authRepository.authState
    .map { authState ->
      SettingsUiState(
        user = (authState as? AuthState.Authenticated)?.user,
        isAuthenticated = authState is AuthState.Authenticated,
      )
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = SettingsUiState()
    )
    
  fun logout() {
    viewModelScope.launch {
      // NOTE: Assume logout isn't fully implemented in local repo, so we just sign out
      authRepository.logout()
    }
  }
}

data class SettingsUiState(
  val user: User? = null,
  val isAuthenticated: Boolean = false,
  val isDarkThemeOn: Boolean = false, // Placeholder for future theme selection
  val areNotificationsEnabled: Boolean = true, // Placeholder
)
