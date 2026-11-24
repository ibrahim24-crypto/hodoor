package com.himo.hodoor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.himo.hodoor.R
import com.himo.hodoor.ui.theme.LogoDeepPurple
import com.himo.hodoor.ui.theme.Space
import com.himo.hodoor.ui.theme.rememberAnimationsEnabled
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val animationsEnabled = rememberAnimationsEnabled()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashLogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) 300 else 0),
        label = "splashLogoAlpha"
    )
    val nameAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animationsEnabled) 300 else 0,
            delayMillis = if (animationsEnabled) 180 else 0
        ),
        label = "splashNameAlpha"
    )

    LaunchedEffect(animationsEnabled) {
        delay(if (animationsEnabled) 800 else 0)
        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(LogoDeepPurple),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo(
            size = 150.dp,
            modifier = Modifier.graphicsLayer {
                alpha = logoAlpha
                scaleX = logoScale
                scaleY = logoScale
            }
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.graphicsLayer { alpha = nameAlpha }
        )
    }
}
