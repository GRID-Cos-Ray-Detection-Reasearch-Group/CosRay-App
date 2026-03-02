@file:Suppress("FunctionNaming")

package com.grid.cosrayapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = HorizonTeal,
    onPrimary = Onyx,
    secondary = EmberGlow,
    onSecondary = Onyx,
    tertiary = Frost,
    background = PolarNight,
    surface = StormGray,
    onSurface = CloudWhite,
    surfaceVariant = SlateMidnight,
    onSurfaceVariant = MistGray,
    error = EmberGlow,
    onError = Onyx,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = HorizonTeal,
    onPrimary = Onyx,
    secondary = EmberGlow,
    onSecondary = Onyx,
    tertiary = Sunray,
    background = CloudWhite,
    surface = CloudWhite,
    onSurface = StormGray,
    surfaceVariant = Frost,
    onSurfaceVariant = MistGray,
    error = EmberGlow,
    onError = Onyx,
  )

@Composable
fun CosRayAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  oledDark: Boolean = false,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }.run {
      if (darkTheme && oledDark) {
        copy(
          background = androidx.compose.ui.graphics.Color.Black,
          surface = androidx.compose.ui.graphics.Color.Black,
          surfaceVariant = androidx.compose.ui.graphics.Color.Black,
        )
      } else {
        this
      }
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
