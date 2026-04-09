package com.grid.cosrayapp.navigation

sealed class CosRayDestination(val route: String) {
  data object Login : CosRayDestination("login")

  data object Register : CosRayDestination("register")

  data object Device : CosRayDestination("device")

  data object DetectorManagement : CosRayDestination("detector_management")

  data object Dashboard : CosRayDestination("dashboard")

  data object DatabaseViewer : CosRayDestination("database_viewer")

  data object Settings : CosRayDestination("settings")

  data object ApiTest : CosRayDestination("api_test")
}
