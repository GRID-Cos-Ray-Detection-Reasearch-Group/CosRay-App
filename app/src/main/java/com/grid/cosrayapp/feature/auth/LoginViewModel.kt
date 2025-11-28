package com.grid.cosrayapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.common.CosRayResult
import com.grid.cosrayapp.core.ui.UiMessage
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.data.auth.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
  private val authRepository: AuthRepository,
  private val validator: AuthValidator,
) : ViewModel() {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  fun onUsernameChanged(value: String) {
    _uiState.update { it.copy(username = value, usernameError = null) }
  }

  fun onEmailChanged(value: String) {
    _uiState.update { it.copy(email = value, emailError = null) }
  }

  fun onPasswordChanged(value: String) {
    _uiState.update { it.copy(password = value, passwordError = null) }
  }

  fun toggleCreateAccount() {
    _uiState.update { state ->
      val nextMode = if (state.mode == AuthMode.Login) AuthMode.Register else AuthMode.Login
      state.copy(
        mode = nextMode,
        errorMessage = null,
        usernameError = null,
        emailError = null,
        passwordError = null,
      )
    }
  }

  @Suppress("LongMethod")
  fun submit() {
    val state = _uiState.value
    val passwordValid = validator.isPasswordStrong(state.password)
    if (state.mode == AuthMode.Login) {
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
    } else {
      val emailValid = validator.isEmailValid(state.email)
      if (!emailValid || !passwordValid) {
        _uiState.update {
          it.copy(
            emailError =
              if (emailValid) {
                null
              } else {
                UiMessage.from(R.string.auth_error_invalid_email)
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
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result =
        if (state.mode == AuthMode.Login) {
          authRepository.login(state.username, state.password)
        } else {
          authRepository.register(state.email, state.password, state.displayName)
        }
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

  fun onDisplayNameChanged(value: String) {
    _uiState.update { it.copy(displayName = value) }
  }
}

data class LoginUiState(
  val username: String = "",
  val email: String = "",
  val password: String = "",
  val displayName: String = "",
  val usernameError: UiMessage? = null,
  val emailError: UiMessage? = null,
  val passwordError: UiMessage? = null,
  val errorMessage: UiMessage? = null,
  val isLoading: Boolean = false,
  val mode: AuthMode = AuthMode.Login,
)

enum class AuthMode {
  Login,
  Register,
}
