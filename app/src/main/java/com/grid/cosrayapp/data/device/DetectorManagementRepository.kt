package com.grid.cosrayapp.data.device

import com.grid.cosrayapp.core.network.CosRayApi
import com.grid.cosrayapp.core.network.model.DeviceDto
import javax.inject.Inject

class DetectorManagementRepository @Inject constructor(private val api: CosRayApi) {
  suspend fun getDevices(accessToken: String): List<DeviceDto> = api.getDevices(accessToken)

  suspend fun registerDevice(
    accessToken: String,
    macAddress: String,
    name: String,
    description: String,
  ): DeviceDto =
    api.createDevice(
      accessToken = accessToken,
      macAddress = macAddress,
      name = name,
      description = description.ifBlank { null },
    )
}
