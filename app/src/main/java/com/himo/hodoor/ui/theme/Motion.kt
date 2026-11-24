package com.himo.hodoor.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/** Read the system animator scale once; false when the user chose "remove animations". */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
}

/** Press feedback without a ripple: scale down while pressed, spring back on release. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    targetScale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Counts 0 → target on first display and tweens smoothly on later changes. */
@Composable
fun animateCountUp(target: Int, durationMs: Int = 500, enabled: Boolean = true): Int {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target, durationMs, enabled) {
        if (enabled) {
            value.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
        } else {
            value.snapTo(target.toFloat())
        }
    }
    return value.value.roundToInt()
}

/** Lightweight finite shimmer placeholder (no library, no infinite animation). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            repeat(2) {
                progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                progress.snapTo(0f)
            }
        }
    }
    val shift = progress.value * 200f
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant
                ),
                start = Offset(shift, 0f),
                end = Offset(shift + 200f, 0f)
            ),
            shape = HodoorShapes.card
        )
    )
}
