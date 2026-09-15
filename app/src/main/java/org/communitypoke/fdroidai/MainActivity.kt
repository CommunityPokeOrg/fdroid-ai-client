package org.communitypoke.fdroidai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.communitypoke.fdroidai.ui.navigation.FdroidNavHost
import org.communitypoke.fdroidai.ui.theme.FdroidAiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FdroidAiApplication).container
        setContent {
            FdroidAiTheme {
                FdroidNavHost(container = container)
            }
        }
    }
}
