package com.himo.hodoor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himo.hodoor.R
import com.himo.hodoor.ui.theme.Space
import kotlin.math.min

/**
 * Fullscreen still photo with a tappable, labelled box around every detected QR code.
 * Detection coordinates live in bitmap space, so the image and all boxes share one
 * LTR container — the app's RTL locale must never mirror them independently.
 */
@Composable
fun SnapshotPicker(
    snapshot: SnapshotUi,
    onPick: (SnapshotItem) -> Unit,
    onDismiss: () -> Unit,
    animationsEnabled: Boolean = true
) {
    BackHandler(enabled = true, onBack = onDismiss)

    val duration = if (animationsEnabled) 200 else 0
    var countReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { countReady = true }
    val countScale by animateFloatAsState(
        targetValue = if (countReady) 1f else 0.8f,
        animationSpec = tween(duration),
        label = "snapshotCount"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val imageBitmap = remember(snapshot.bitmap) { snapshot.bitmap.asImageBitmap() }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val imageWidth = snapshot.bitmap.width.coerceAtLeast(1).toFloat()
            val imageHeight = snapshot.bitmap.height.coerceAtLeast(1).toFloat()
            val fitScale = min(maxWidth.value / imageWidth, maxHeight.value / imageHeight)
            val displayWidth = (imageWidth * fitScale).dp
            val displayHeight = (imageHeight * fitScale).dp
            val baseX = ((maxWidth.value - displayWidth.value) / 2f).dp
            val baseY = ((maxHeight.value - displayHeight.value) / 2f).dp

            var zoom by remember(snapshot) { mutableStateOf(1f) }
            var pan by remember(snapshot) { mutableStateOf(Offset.Zero) }

            val gestureModifier = Modifier
                .fillMaxSize()
                .pointerInput(snapshot, maxWidth, maxHeight, displayWidth, displayHeight) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                        zoom = nextZoom
                        val limitX =
                            (maxOf(0f, displayWidth.value * nextZoom - maxWidth.value) / 2f).dp.toPx()
                        val limitY =
                            (maxOf(0f, displayHeight.value * nextZoom - maxHeight.value) / 2f).dp.toPx()
                        pan = Offset(
                            x = (pan.x + panChange.x).coerceIn(-limitX, limitX),
                            y = (pan.y + panChange.y).coerceIn(-limitY, limitY)
                        )
                    }
                }
                .pointerInput(snapshot) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (zoom > 1.01f) {
                            zoom = 1f
                            pan = Offset.Zero
                        } else {
                            val nextZoom = 2.5f
                            zoom = nextZoom
                            val limitX = (maxOf(
                                0f,
                                displayWidth.value * nextZoom - maxWidth.value
                            ) / 2f).dp.toPx()
                            val limitY = (maxOf(
                                0f,
                                displayHeight.value * nextZoom - maxHeight.value
                            ) / 2f).dp.toPx()
                            val focusX = tap.x - size.width / 2f
                            val focusY = tap.y - size.height / 2f
                            pan = Offset(
                                x = (-focusX * (nextZoom - 1f)).coerceIn(-limitX, limitX),
                                y = (-focusY * (nextZoom - 1f)).coerceIn(-limitY, limitY)
                            )
                        }
                    })
                }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(modifier = gestureModifier) {
                    Box(
                        modifier = Modifier
                            .offset(x = baseX, y = baseY)
                            .size(displayWidth, displayHeight)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        snapshot.items.forEach { item ->
                            QrBox(item, fitScale, onPick)
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.sm)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                text = stringResource(
                    R.string.scan_snapshot_count,
                    snapshot.items.size
                ),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { scaleX = countScale; scaleY = countScale }
                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                    .padding(horizontal = Space.sm, vertical = 6.dp)
            )
            Spacer(Modifier.width(Space.sm))
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.scan_rescan), color = Color.White)
            }
        }
    }
}

@Composable
private fun QrBox(
    item: SnapshotItem,
    fitScale: Float,
    onPick: (SnapshotItem) -> Unit
) {
    val known = item.code != null && item.studentName != null
    val color = if (known) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val left = (item.detection.left * fitScale).dp
    val top = (item.detection.top * fitScale).dp
    val width = ((item.detection.right - item.detection.left).coerceAtLeast(2) * fitScale).dp
    val height = ((item.detection.bottom - item.detection.top).coerceAtLeast(2) * fitScale).dp
    val label = item.studentName ?: stringResource(R.string.scan_unknown_code)
    val labelY = if (top > 30.dp) top - 28.dp else top + height + 4.dp

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width, height)
            .drawBehind {
                val radius = CornerRadius(8.dp.toPx())
                drawRoundRect(color = color.copy(alpha = 0.16f), cornerRadius = radius)
                drawRoundRect(
                    color = color,
                    cornerRadius = radius,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            .clickable { onPick(item) }
    )

    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        maxLines = 1,
        modifier = Modifier
            .offset(x = left, y = labelY)
            .background(color.copy(alpha = 0.92f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable { onPick(item) }
    )
}
