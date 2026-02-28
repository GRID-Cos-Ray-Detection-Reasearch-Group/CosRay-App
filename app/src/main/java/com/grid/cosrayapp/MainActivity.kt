package com.grid.cosrayapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grid.cosrayapp.data.auth.AuthRepository
import com.grid.cosrayapp.ui.CosRayApp
import com.grid.cosrayapp.ui.theme.CosRayAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject
  lateinit var authRepository: AuthRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CosRayAppTheme {
        CosRayApp(
          authRepository = authRepository,
        )
      }
    }
  }
}
