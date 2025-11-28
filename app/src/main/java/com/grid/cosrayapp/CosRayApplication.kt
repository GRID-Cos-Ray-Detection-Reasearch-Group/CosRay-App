package com.grid.cosrayapp

import android.app.Application
import com.grid.cosrayapp.core.di.AppContainer
import com.grid.cosrayapp.core.di.AppContainerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CosRayApplication : Application() {
  lateinit var appContainer: AppContainer
    private set

  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    appContainer = AppContainerImpl(appContext = this, externalScope = applicationScope)
  }
}
