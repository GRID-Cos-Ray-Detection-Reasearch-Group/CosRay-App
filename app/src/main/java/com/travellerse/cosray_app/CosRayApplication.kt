package com.travellerse.cosray_app

import android.app.Application
import com.travellerse.cosray_app.core.di.AppContainer
import com.travellerse.cosray_app.core.di.AppContainerImpl
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
