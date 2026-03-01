package com.grid.cosrayapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.ui.UiMessage
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  fun onUsernameChanged(value: String) {
    _uiState.update { it.copy(username = value, usernameError = null) }
  }

  fun onPasswordChanged(value: String) {
    _uiState.update { it.copy(password = value, passwordError = null) }
  }

  fun submit() {
    val state = _uiState.value
    val passwordValid = AuthValidator.isPasswordStrong(state.password)
    val usernameValid = state.username.isNotBlank()
    if (!usernameValid || !passwordValid) {
      _uiState.update {
        it.copy(
          usernameError =
            if (usernameValid) {
              null
            } else {
              UiMessage.from(R.string.auth_error_invalid_username)
            },
          passwordError =
            if (passwordValid) {
              null
            } else {
              UiMessage.from(R.string.auth_error_weak_password)
            },
        )
      }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.login(state.username, state.password)
      when (result) {
        is CosRayResult.Success -> {
          _uiState.update { it.copy(isLoading = false) }
        }
        is CosRayResult.Error -> {
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage =
                result.throwable.message?.let(UiMessage::fromRaw)
                  ?: UiMessage.from(R.string.error_unknown),
            )
          }
        }
      }
    }
  }
}

data class LoginUiState(
  val username: String = "",
  val password: String = "",
  val usernameError: UiMessage? = null,
  val passwordError: UiMessage? = null,
  val errorMessage: UiMessage? = null,
  val isLoading: Boolean = false,
)
