package com.grid.cosrayapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.ui.CosRayApp
import com.grid.cosrayapp.ui.theme.CosRayAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var authRepository: AuthRepository

  @Inject
  lateinit var userPreferences: com.grid.cosrayapp.core.datastore.UserPreferencesDataSource

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val darkTheme by userPreferences.darkTheme.collectAsStateWithLifecycle(initialValue = null)
      val oledDark by userPreferences.oledDark.collectAsStateWithLifecycle(initialValue = null)

      CosRayAppTheme(
        darkTheme = darkTheme ?: androidx.compose.foundation.isSystemInDarkTheme(),
        oledDark = oledDark ?: false,
      ) {
        CosRayApp(authRepository = authRepository)
      }
    }
  }
}
