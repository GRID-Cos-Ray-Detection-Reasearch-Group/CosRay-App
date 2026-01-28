package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.core.network.model.DeviceDto

/**
 * Request to register a new device.
 *
 * @property macAddress Device MAC address (format: AA:BB:CC:DD:EE:FF).
 * @property name User-defined device name.
 * @property description Optional device description.
 */
data class RegisterDeviceRequest(
  val macAddress: String,
  val name: String,
  val description: String? = null,
)

/**
 * Request to update device information.
 *
 * All fields are optional - only provided fields will be updated.
 *
 * @property name New device name.
 * @property description New device description.
 * @property isActive Whether the device is active.
 */
data class UpdateDeviceRequest(
  val name: String? = null,
  val description: String? = null,
  val isActive: Boolean? = null,
)

/**
 * API interface for device management operations.
 *
 * Handles device registration, listing, updating, and deletion.
 */
interface DeviceApi {
  /**
   * Register a new device.
   *
   * @param accessToken Current access token.
   * @param request Device registration details.
   * @return Registered device information.
   */
  suspend fun registerDevice(
    accessToken: String,
    request: RegisterDeviceRequest,
  ): ApiResult<DeviceDto>

  /**
   * Get all devices for the current user.
   *
   * @param accessToken Current access token.
   * @return List of devices.
   */
  suspend fun getDevices(accessToken: String): ApiResult<List<DeviceDto>>

  /**
   * Get a specific device by ID.
   *
   * @param accessToken Current access token.
   * @param deviceId Device ID.
   * @return Device information.
   */
  suspend fun getDevice(accessToken: String, deviceId: Int): ApiResult<DeviceDto>

  /**
   * Update device information.
   *
   * @param accessToken Current access token.
   * @param deviceId Device ID.
   * @param request Update details.
   * @return Updated device information.
   */
  suspend fun updateDevice(
    accessToken: String,
    deviceId: Int,
    request: UpdateDeviceRequest,
  ): ApiResult<DeviceDto>

  /**
   * Delete a device.
   *
   * @param accessToken Current access token.
   * @param deviceId Device ID.
   * @return Unit on success.
   */
  suspend fun deleteDevice(accessToken: String, deviceId: Int): ApiResult<Unit>
}
