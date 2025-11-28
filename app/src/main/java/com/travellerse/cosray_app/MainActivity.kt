package com.travellerse.cosray_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.travellerse.cosray_app.core.di.LocalAppContainer
import com.travellerse.cosray_app.ui.CosRayApp
import com.travellerse.cosray_app.ui.theme.CosRayAppTheme

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
