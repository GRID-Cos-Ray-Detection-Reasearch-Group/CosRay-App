package com.grid.cosrayapp.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpClientFactoryRedactionTest {
  @Test
  fun `redaction should remove Authorization bearer token`() {
    val raw = "Authorization: Bearer abc.def.ghi"
    val redacted = HttpClientFactory.redactNetworkLog(raw)
    assertFalse(redacted.contains("abc.def.ghi"))
    assertTrue(redacted.contains("[REDACTED]"))
  }

  @Test
  fun `redaction should remove token fields in json`() {
    val raw = """{"access":"a1","refresh":"r1","access_token":"a2","refresh_token":"r2"}"""
    val redacted = HttpClientFactory.redactNetworkLog(raw)
    assertFalse(redacted.contains("\"a1\""))
    assertFalse(redacted.contains("\"r1\""))
    assertFalse(redacted.contains("\"a2\""))
    assertFalse(redacted.contains("\"r2\""))
    assertTrue(redacted.contains("[REDACTED]"))
  }
}
