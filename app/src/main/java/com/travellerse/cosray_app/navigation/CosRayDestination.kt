package com.travellerse.cosray_app.navigation

sealed class CosRayDestination(val route: String) {
    data object Login : CosRayDestination("login")
    data object Device : CosRayDestination("device")
    data object Dashboard : CosRayDestination("dashboard")
}
