package org.communitypoke.fdroidai.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.R

/**
 * In-app loading screen shown right after the native Android 12+ splash:
 * the full `splash_loading` artwork (cartoon otter + "Built by Poke x Devin"
 * credit). The artwork's white background is matched by the surface so it
 * reads as a full-bleed loading screen on any aspect ratio.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(700)) }
        launch { alpha.animateTo(1f, tween(700)) }
        delay(1_900)
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_loading),
            contentDescription = "F-Droid AI loading screen — otter mascot, Built by Poke x Devin",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale.value)
                .alpha(alpha.value),
        )
    }
}
