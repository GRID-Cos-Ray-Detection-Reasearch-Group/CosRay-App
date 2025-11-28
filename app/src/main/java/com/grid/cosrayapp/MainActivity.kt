package com.grid.cosrayapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.grid.cosrayapp.core.di.LocalAppContainer
import com.grid.cosrayapp.ui.CosRayApp
import com.grid.cosrayapp.ui.theme.CosRayAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as CosRayApplication).appContainer
    setContent {
      CompositionLocalProvider(LocalAppContainer provides appContainer) {
        CosRayAppTheme { CosRayApp() }
      }
    }
  }
}
