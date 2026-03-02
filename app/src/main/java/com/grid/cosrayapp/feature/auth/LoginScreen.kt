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
  onPasswordChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onContinueAsGuest: () -> Unit,
) {
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
        text = stringResource(R.string.auth_welcome_back),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
      )
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      tonalElevation = 2.dp,
      shadowElevation = 4.dp,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        OutlinedTextField(
          value = state.username,
          onValueChange = onUsernameChange,
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.auth_username_label)) },
          isError = state.usernameError != null,
          singleLine = true,
        )
        ErrorText(state.usernameError?.asString())

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

        Button(
          onClick = onSubmit,
          modifier = Modifier.fillMaxWidth(),
          enabled = !state.isLoading,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          shape = RoundedCornerShape(12.dp)
        ) {
          if (state.isLoading) {
            Text(
              text = stringResource(R.string.auth_loading),
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            Text(
              text = stringResource(R.string.auth_login_action),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        ErrorText(state.errorMessage?.asString(), alignCenter = true)
      }
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
