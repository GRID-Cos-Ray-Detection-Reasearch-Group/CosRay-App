package com.travellerse.cosray_app.domain.model

sealed interface DeviceConnectionState {
    data object Disconnected : DeviceConnectionState
    data class Connecting(val device: BleDevice) : DeviceConnectionState
    data class Connected(val device: BleDevice) : DeviceConnectionState
    data class Failed(val reason: String?) : DeviceConnectionState
}
