package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import com.grid.cosrayapp.core.network.model.PacketUploadResponse

/**
 * Response for batch upload operations.
 *
 * @property successCount Number of successfully uploaded packets.
 * @property failedCount Number of failed uploads.
 * @property errors List of error messages for failed uploads.
 */
data class BatchUploadResponse(
  val successCount: Int,
  val failedCount: Int,
  val errors: List<String> = emptyList(),
)

/**
 * API interface for telemetry data upload operations.
 *
 * Handles uploading muon and timeline data packets to the backend.
 */
interface TelemetryApi {
  /**
   * Upload a single data packet.
   *
   * @param accessToken Current access token.
   * @param request Packet upload request containing device ID and packet data.
   * @return Upload response with confirmation.
   */
  suspend fun uploadPacket(
    accessToken: String,
    request: PacketUploadRequest,
  ): ApiResult<PacketUploadResponse>

  /**
   * Upload multiple data packets in a batch.
   *
   * @param accessToken Current access token.
   * @param requests List of packet upload requests.
   * @return Batch upload response with success/failure counts.
   */
  suspend fun uploadBatch(
    accessToken: String,
    requests: List<PacketUploadRequest>,
  ): ApiResult<BatchUploadResponse>
}
