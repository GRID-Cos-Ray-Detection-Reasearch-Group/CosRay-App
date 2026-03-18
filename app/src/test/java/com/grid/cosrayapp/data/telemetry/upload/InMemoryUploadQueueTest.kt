package com.grid.cosrayapp.data.telemetry.upload

import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryUploadQueueTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `enqueue beyond maxSize should drop oldest`() = runTest {
    val queue = InMemoryUploadQueue(json = json, maxSize = 3)
    val requests =
      (1..5).map { index ->
        PacketUploadRequest(device = "D$index", packetType = "muon")
      }

    queue.enqueue(requests)

    val batch = queue.peekBatch(limit = 10)
    assertEquals(3, batch.size)
    assertEquals("D3", batch[0].request.device)
    assertEquals("D4", batch[1].request.device)
    assertEquals("D5", batch[2].request.device)
    assertEquals(2L, queue.dropCount())
  }
}
