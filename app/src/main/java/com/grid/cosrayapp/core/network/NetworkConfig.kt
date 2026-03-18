package com.grid.cosrayapp.core.network

import com.grid.cosrayapp.BuildConfig
import io.ktor.http.URLBuilder

object NetworkConfig {
  const val baseUrl: String = BuildConfig.BASE_URL

  val baseUrlHost: String
    get() = URLBuilder(baseUrl).build().host
}
