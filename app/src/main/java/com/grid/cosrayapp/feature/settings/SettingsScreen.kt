package com.grid.cosrayapp.feature.settings

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grid.cosrayapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  state: SettingsUiState,
  onLogout: () -> Unit,
  onToggleDarkTheme: (Boolean) -> Unit,
  onToggleOledDark: (Boolean) -> Unit,
  onOpenDrawer: () -> Unit,
) {
  val context = LocalContext.current
  val versionInfo =
    remember {
      try {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
      } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
      }
    }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onOpenDrawer) {
            Icon(
              imageVector = Icons.Default.Menu,
              contentDescription = stringResource(R.string.settings_navigation_menu),
            )
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {

      // Account Section
      SettingsSection(title = stringResource(R.string.settings_section_account)) {
        if (state.isAuthenticated) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  state.user?.displayName ?: stringResource(R.string.settings_user_name_default),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = state.user?.email ?: stringResource(R.string.settings_user_login_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(stringResource(R.string.device_logout_action))
          }
        } else {
          Text(
            text = stringResource(R.string.settings_not_logged_in),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
          )
        }
      }

      // Preferences Section
      SettingsSection(title = stringResource(R.string.settings_section_preferences)) {
        SettingsToggleRow(
          icon = Icons.Default.Palette,
          label = stringResource(R.string.settings_dark_theme),
          checked = state.isDarkThemeOn,
          onCheckedChange = onToggleDarkTheme,
        )
        if (state.isDarkThemeOn) {
          SettingsToggleRow(
            icon = Icons.Default.DarkMode,
            label = stringResource(R.string.settings_oled_dark_mode),
            checked = state.isOledDarkOn,
            onCheckedChange = onToggleOledDark,
          )
        }
        SettingsToggleRow(
          icon = Icons.Default.Notifications,
          label = stringResource(R.string.settings_notifications),
          checked = state.areNotificationsEnabled,
          onCheckedChange = { /* Placeholder */ },
        )
      }

      // About Section
      SettingsSection(title = stringResource(R.string.settings_section_about)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
              text = stringResource(R.string.settings_version),
              style = MaterialTheme.typography.bodyLarge,
            )
          }
          Text(
            text = "v$versionInfo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 4.dp),
    )
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
      }
    }
  }
}

@Composable
private fun SettingsToggleRow(
  icon: ImageVector,
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
