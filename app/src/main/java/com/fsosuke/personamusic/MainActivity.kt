package com.fsosuke.personamusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.ui.PersonaMusicApp
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.theme.PersonaMusicTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as PersonaApp).container

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            // The media notification (lock-screen controls) needs this on Android 13+.
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                PersonaMusicTheme(theme) {
                    PersonaMusicApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
        container.updates.checkIfDue()
    }

    override fun onStop() {
        container.player.disconnect()
        super.onStop()
    }
}
