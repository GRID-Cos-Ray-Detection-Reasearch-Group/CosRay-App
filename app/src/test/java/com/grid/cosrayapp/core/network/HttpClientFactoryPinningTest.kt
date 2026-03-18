package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpClientFactoryPinningTest {
  @Test
  fun `debug build should not enable pinning`() {
    assertFalse(!BuildConfig.DEBUG && BuildConfig.CERT_PINS.isNotEmpty())
  }

  @Test
  fun `debug build should include no pins`() {
    assertTrue(BuildConfig.CERT_PINS.isEmpty())
  }
}
