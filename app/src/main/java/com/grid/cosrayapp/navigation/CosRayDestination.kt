package com.grid.cosrayapp.navigation

sealed class CosRayDestination(val route: String) {
  data object Login : CosRayDestination("login")

  data object Device : CosRayDestination("device")

  data object Dashboard : CosRayDestination("dashboard")

  data object Settings : CosRayDestination("settings")

  data object ApiTest : CosRayDestination("api_test")
}
