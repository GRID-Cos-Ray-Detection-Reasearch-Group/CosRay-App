package com.travellerse.cosray_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.DisposableEffect
import com.traveller.cosray_app.protocol.Protocol
import com.travellerse.cosray_app.core.di.LocalAppContainer
import com.travellerse.cosray_app.ui.CosRayApp
import com.travellerse.cosray_app.ui.theme.CosRayAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as CosRayApplication).appContainer
        val bleRepository = appContainer.bleRepository
        setContent {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                CosRayAppTheme {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                val commandFrame = Protocol.Command.buildRequestFrame()
                                lifecycleScope.launch {
                                    if (bleRepository.hasPermissions() &&
                                        bleRepository.connectionState.value is DeviceConnectionState.Connected) {
                                        bleRepository.sendCommand(commandFrame)
                                    } else {
                                    }
                                }
                            }
                        ) {
                            Text("Ask for Request")
                        }
                    CosRayApp()
                }
            }
        }
    }
}
