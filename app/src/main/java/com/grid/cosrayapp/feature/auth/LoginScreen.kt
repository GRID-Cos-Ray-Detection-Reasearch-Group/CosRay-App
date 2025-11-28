@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.grid.cosrayapp.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grid.cosrayapp.R
import com.grid.cosrayapp.core.ui.asString

@Composable
fun LoginScreen(
  state: LoginUiState,
  onUsernameChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onDisplayNameChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onToggleMode: () -> Unit,
  onContinueAsGuest: () -> Unit,
) {
  val isRegistering = state.mode == AuthMode.Register
  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = stringResource(R.string.login_brand_title),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text =
          if (isRegistering) {
            stringResource(R.string.auth_register_title)
          } else {
            stringResource(R.string.auth_welcome_back)
          },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
      )
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      tonalElevation = 8.dp,
      shadowElevation = 8.dp,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val primaryFieldValue = if (isRegistering) state.email else state.username
        val primaryFieldError = if (isRegistering) state.emailError else state.usernameError
        val primaryFieldLabel =
          if (isRegistering) {
            R.string.auth_email_label
          } else {
            R.string.auth_username_label
          }
        val primaryFieldCallback = if (isRegistering) onEmailChange else onUsernameChange

        OutlinedTextField(
          value = primaryFieldValue,
          onValueChange = primaryFieldCallback,
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(primaryFieldLabel)) },
          isError = primaryFieldError != null,
          singleLine = true,
        )
        ErrorText(primaryFieldError?.asString())

        OutlinedTextField(
          value = state.password,
          onValueChange = onPasswordChange,
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.auth_password_label)) },
          isError = state.passwordError != null,
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
        )
        ErrorText(state.passwordError?.asString())

        AnimatedVisibility(visible = isRegistering) {
          OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_display_name_label)) },
            singleLine = true,
          )
        }

        Button(
          onClick = onSubmit,
          modifier = Modifier.fillMaxWidth(),
          enabled = !state.isLoading,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          if (state.isLoading) {
            Text(
              text = stringResource(R.string.auth_loading),
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            Text(
              text =
                if (isRegistering) {
                  stringResource(R.string.auth_register_action)
                } else {
                  stringResource(R.string.auth_login_action)
                },
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        ErrorText(state.errorMessage?.asString(), alignCenter = true)
      }
    }

    TextButton(onClick = onToggleMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
      Text(
        text =
          if (isRegistering) {
            stringResource(R.string.auth_have_account_action)
          } else {
            stringResource(R.string.auth_create_account_action)
          },
        style = MaterialTheme.typography.labelLarge,
      )
    }

    TextButton(
      onClick = onContinueAsGuest,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
      Text(
        text = stringResource(R.string.auth_skip_login_action),
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
private fun ErrorText(message: String?, alignCenter: Boolean = false) {
  AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
    Text(
      text = message ?: return@AnimatedVisibility,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
      textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
