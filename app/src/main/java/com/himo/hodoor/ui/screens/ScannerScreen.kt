package com.himo.hodoor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himo.hodoor.R
import com.himo.hodoor.scanner.FrameInfo
import com.himo.hodoor.scanner.QrAnalyzer
import com.himo.hodoor.scanner.QrDetection
import com.himo.hodoor.scanner.SCAN_FRAME_FRACTION
import com.himo.hodoor.scanner.downscaleBitmap
import com.himo.hodoor.scanner.rotateBitmap
import com.himo.hodoor.ui.settings.AppSettings
import com.himo.hodoor.ui.theme.HodoorShapes
import com.himo.hodoor.ui.theme.LocalHodoorColors
import com.himo.hodoor.ui.theme.Space
import com.himo.hodoor.ui.theme.rememberAnimationsEnabled
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

private enum class ScannerMode { SINGLE, MULTI }

/** Longer confirmation window for the "slow" setting (was 700 ms). */
private const val SLOW_SCAN_DELAY_MS = 1500L

/** Mutable 700 ms union window; a plain holder avoids stale captures in analyzer callbacks. */
private class ScanWindow {
    var start = 0L
    var decisionMade = false
    val detections = LinkedHashMap<String, QrDetection>()
    var frameInfo: FrameInfo? = null

    fun reset() {
        start = 0L
        decisionMade = false
        detections.clear()
        frameInfo = null
    }
}

/** Eases box movement so small hand shakes do not make frames jump or vanish. */
private fun blendDetection(old: QrDetection, next: QrDetection): QrDetection {
    val weight = 0.6f
    fun mix(from: Int, to: Int) = (from + (to - from) * weight).toInt()
    fun mix(from: Float, to: Float) = from + (to - from) * weight
    val corners = if (old.corners.size == next.corners.size && next.corners.isNotEmpty()) {
        old.corners.zip(next.corners) { first, second ->
            mix(first.first, second.first) to mix(first.second, second.second)
        }
    } else {
        next.corners
    }
    return QrDetection(
        payload = next.payload,
        left = mix(old.left, next.left),
        top = mix(old.top, next.top),
        right = mix(old.right, next.right),
        bottom = mix(old.bottom, next.bottom),
        corners = corners,
        inScanFrame = next.inScanFrame
    )
}

/**
 * Keeps live boxes for a few missed frames. ML Kit can miss a code while the phone moves;
 * without this grace period every box disappears and reappears on each shaky frame.
 */
private class DetectionTracker {
    private class Tracked(
        var detection: QrDetection,
        var lastSeen: Long,
        var misses: Int
    )

    private val tracked = LinkedHashMap<String, Tracked>()

    fun update(detections: List<QrDetection>, now: Long): List<QrDetection> {
        val seen = HashSet<String>()
        detections.forEach { detection ->
            seen += detection.payload
            val previous = tracked[detection.payload]
            if (previous == null) {
                tracked[detection.payload] = Tracked(detection, now, 0)
            } else {
                previous.detection = blendDetection(previous.detection, detection)
                previous.lastSeen = now
                previous.misses = 0
            }
        }
        tracked.entries.removeAll { (payload, item) ->
            payload !in seen && (++item.misses >= 3 || now - item.lastSeen > 700L)
        }
        return tracked.values.map { it.detection }
    }

    fun clear() {
        tracked.clear()
    }
}

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onStudentFound: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val animationsEnabled = rememberAnimationsEnabled()
    val codes by viewModel.codes.collectAsStateWithLifecycle()
    val matchedCode by viewModel.matchedCode.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val imageError by viewModel.imageError.collectAsStateWithLifecycle()
    val notFoundMessage by viewModel.notFoundMessage.collectAsStateWithLifecycle()
    val scanConfirmation = remember { AppSettings.scanConfirmation(context) }
    var mode by remember {
        mutableStateOf(
            if (AppSettings.scanMode(context) == "multi") ScannerMode.MULTI else ScannerMode.SINGLE
        )
    }
    var useImageMode by remember { mutableStateOf(false) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.scanImage(context.applicationContext, uri) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var lastMultiFrame by remember { mutableStateOf<Pair<Bitmap, Int>?>(null) }
    var captureInProgress by remember { mutableStateOf(false) }
    var shutterTrigger by remember { mutableStateOf(0) }
    val shutter = remember { Animatable(0f) }
    var torchOn by remember { mutableStateOf(false) }
    var scanSession by remember { mutableStateOf(0) }
    val window = remember { ScanWindow() }
    val tracker = remember { DetectionTracker() }
    var windowTick by remember { mutableStateOf(0L) }
    var visibleDetections by remember { mutableStateOf<List<QrDetection>>(emptyList()) }
    var detectionFrame by remember { mutableStateOf<FrameInfo?>(null) }
    var triggerCapture by remember {
        mutableStateOf<(Boolean, List<QrDetection>, FrameInfo?) -> Unit>({ _, _, _ -> })
    }
    var clearAnalysis by remember { mutableStateOf<() -> Unit>({}) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(shutterTrigger) {
        if (shutterTrigger > 0) {
            shutter.snapTo(0.8f)
            shutter.animateTo(0f, tween(if (animationsEnabled) 150 else 0))
        }
    }

    fun setMode(next: ScannerMode) {
        if (mode == next) return
        AppSettings.setScanMode(context, if (next == ScannerMode.MULTI) "multi" else "single")
        mode = next
        window.reset()
        windowTick = 0L
        viewModel.retry()
        scanSession++
    }

    // SINGLE mode: decide only after a 700 ms union of distinct detections.
    LaunchedEffect(windowTick) {
        val tick = windowTick
        if (tick <= 0L) return@LaunchedEffect
        delay(SLOW_SCAN_DELAY_MS)
        if (window.start != tick) return@LaunchedEffect
        val union = window.detections.values.toList()
        val info = window.frameInfo
        window.start = 0L
        window.detections.clear()
        window.frameInfo = null
        windowTick = 0L
        if (window.decisionMade || !scanning) return@LaunchedEffect
        window.decisionMade = true
        when {
            union.size == 1 -> viewModel.onScanResult(listOf(union.first().payload))
            union.size >= 2 -> {
                clearAnalysis()
                triggerCapture(true, union, info)
            }
        }
    }

    LaunchedEffect(scanSession, mode) {
        window.reset()
        tracker.clear()
        windowTick = 0L
        visibleDetections = emptyList()
        detectionFrame = null
    }

    val submitPhoto: (
        bitmap: Bitmap,
        fullBitmap: Bitmap?,
        forcePicker: Boolean,
        fallback: List<QrDetection>,
        frameInfo: FrameInfo?
    ) -> Unit = { bitmap, fullBitmap, forcePicker, fallback, frameInfo ->
        captureInProgress = false
        lastMultiFrame = null
        viewModel.onPhoto(
            bitmap = bitmap,
            fullBitmap = fullBitmap,
            forcePicker = forcePicker || mode == ScannerMode.MULTI,
            fallbackDetections = fallback,
            frameInfo = frameInfo,
            emptyMessage = context.getString(R.string.scan_multi_empty)
        )
    }

    LaunchedEffect(matchedCode) {
        matchedCode?.let { code ->
            vibrate(context)
            onStudentFound(code)
        }
    }

    if (!useImageMode && !permissionGranted) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                shape = HodoorShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(Space.xl)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                    modifier = Modifier.padding(Space.xl)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.scan_permission_required),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = HodoorShapes.button
                    ) {
                        Text(stringResource(R.string.scan_grant))
                    }
                }
            }
        }
        return
    }

    DisposableEffect(
        permissionGranted, useImageMode, codes, scanSession, matchedCode, scanning, mode
    ) {
        if (useImageMode || !scanning || matchedCode != null || codes.isEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var analyzer: QrAnalyzer? = null
        val executor: Executor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraRotation = previewView.display?.rotation ?: 0
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                            )
                        )
                        .build()
                )
                .setTargetRotation(cameraRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis = analysis
            captureInProgress = false
            lastMultiFrame = null

            clearAnalysis = { analysis.clearAnalyzer() }
            val qrAnalyzer = QrAnalyzer(
                multiMode = mode == ScannerMode.MULTI,
                onResult = { frameInfo, detections ->
                    detectionFrame = frameInfo
                    visibleDetections = tracker.update(detections, SystemClock.elapsedRealtime())
                    val centered = if (mode == ScannerMode.SINGLE) {
                        detections.filter { it.inScanFrame }
                    } else {
                        detections
                    }
                    if (mode == ScannerMode.SINGLE && scanConfirmation == "slow" &&
                        centered.isNotEmpty() && !window.decisionMade && scanning
                    ) {
                        if (window.start == 0L) {
                            window.start = SystemClock.elapsedRealtime()
                            window.decisionMade = false
                            window.frameInfo = frameInfo
                            windowTick = window.start
                        }
                        for (detection in centered) {
                            if (!window.detections.containsKey(detection.payload)) {
                                window.detections[detection.payload] = detection
                            }
                        }
                    }
                },
                onFrame = { frame, frameRotation -> lastMultiFrame = frame to frameRotation },
                previewSize = { previewView.width to previewView.height },
                previewTransform = { previewView.getSensorToViewTransform() }
            )
            analyzer = qrAnalyzer
            analysis.setAnalyzer(executor, qrAnalyzer)

            fun resumeAnalysis() {
                captureInProgress = false
                window.reset()
                windowTick = 0L
                analysis.setAnalyzer(executor, qrAnalyzer)
            }

            fun useFallbackFrame(
                forcePicker: Boolean,
                fallback: List<QrDetection>,
                frameInfo: FrameInfo?
            ) {
                val frame = lastMultiFrame
                if (frame == null) {
                    Log.w("HodoorScanner", "Capture fallback: no frame available")
                    captureInProgress = false
                    viewModel.onCaptureFailed(
                        context.getString(
                            if (mode == ScannerMode.MULTI) R.string.scan_multi_empty
                            else R.string.scan_not_found
                        )
                    )
                    return
                }
                runCatching {
                    val original = rotateBitmap(frame.first, frame.second)
                    val display = downscaleBitmap(original, 2400)
                    display to original
                }.onSuccess { (display, original) ->
                    submitPhoto(display, original, forcePicker, fallback, frameInfo)
                }.onFailure { error ->
                    Log.e("HodoorScanner", "Fallback frame conversion failed", error)
                    resumeAnalysis()
                }
            }

            triggerCapture = { forcePicker, fallback, frameInfo ->
                Log.d("HodoorScanner", "Capture requested: forcePicker=$forcePicker codes=${fallback.size}")
                if (!captureInProgress) {
                    captureInProgress = true
                    shutterTrigger++
                    val capture = imageCapture
                    if (capture == null) {
                        useFallbackFrame(forcePicker, fallback, frameInfo)
                    } else {
                        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                Log.d("HodoorScanner", "Photo captured ${image.width}x${image.height}")
                                val rotation = image.imageInfo.rotationDegrees
                                val result = runCatching {
                                    val original = rotateBitmap(image.toBitmap(), rotation)
                                    val display = downscaleBitmap(original, 2400)
                                    display to original
                                }.getOrNull()
                                image.close()
                                if (result != null) {
                                    submitPhoto(result.first, result.second, forcePicker, fallback, frameInfo)
                                } else {
                                    useFallbackFrame(forcePicker, fallback, frameInfo)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("HodoorScanner", "Photo capture failed", exception)
                                useFallbackFrame(forcePicker, fallback, frameInfo)
                            }
                        })
                    }
                }
            }

            val captureSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                .build()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(captureSelector)
                .setTargetRotation(cameraRotation)
                .build()
            imageCapture = capture

            val fullGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analysis)
                .addUseCase(capture)
                .apply { previewView.viewPort?.let { setViewPort(it) } }
                .build()
            val bound = runCatching {
                provider!!.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    fullGroup
                )
            }
            if (bound.isFailure) {
                Log.w("HodoorScanner", "3-use-case binding failed; retrying without ImageCapture", bound.exceptionOrNull())
                imageCapture = null
                val basicGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(analysis)
                    .apply { previewView.viewPort?.let { setViewPort(it) } }
                    .build()
                runCatching {
                    camera = provider!!.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        basicGroup
                    )
                }.onFailure { error ->
                    Log.e("HodoorScanner", "Camera binding failed", error)
                }
            } else {
                camera = bound.getOrNull()
            }
        }, executor)

        onDispose {
            analyzer?.close()
            provider?.unbindAll()
            camera = null
            imageCapture = null
            imageAnalysis = null
            lastMultiFrame = null
            captureInProgress = false
            clearAnalysis = {}
            triggerCapture = { _, _, _ -> }
        }
    }

    Box(
        modifier.fillMaxSize().then(
            if (useImageMode) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier
        )
    ) {
        if (useImageMode) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_scan),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(R.string.scan_image_hint),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(onClick = { imageLauncher.launch("image/*") }) {
                    Text(stringResource(R.string.scan_image_pick))
                }
                imageError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (scanning && !notFound) {
                DetectionBoxes(
                    detections = visibleDetections,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (scanning && !notFound && mode == ScannerMode.SINGLE) {
                ScanFrame(
                    modifier = Modifier.fillMaxSize(),
                    success = matchedCode != null,
                    animationsEnabled = animationsEnabled
                )
            }
        }

        AppLogo(
            size = 40.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            FilterChip(
                selected = mode == ScannerMode.SINGLE,
                onClick = { setMode(ScannerMode.SINGLE) },
                label = { Text(stringResource(R.string.scan_mode_single)) }
            )
            FilterChip(
                selected = mode == ScannerMode.MULTI,
                onClick = { setMode(ScannerMode.MULTI) },
                label = { Text(stringResource(R.string.scan_mode_multi)) }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp)
        ) {
            FilterChip(
                selected = !useImageMode,
                onClick = {
                    if (useImageMode) {
                        useImageMode = false
                        viewModel.retry()
                        scanSession++
                    }
                },
                label = { Text(stringResource(R.string.scan_mode_camera)) }
            )
            FilterChip(
                selected = useImageMode,
                onClick = { useImageMode = true },
                label = { Text(stringResource(R.string.scan_mode_image)) }
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = if (useImageMode) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }

        if (!useImageMode) {
            val torchProgress by animateFloatAsState(
                targetValue = if (torchOn) 1f else 0f,
                animationSpec = tween(200),
                label = "torchTint"
            )
            val torchTint = lerp(Color.White, Color(0xFFFFB300), torchProgress)
            IconButton(
                onClick = {
                    val next = !torchOn
                    runCatching { camera?.cameraControl?.enableTorch(next) }
                    torchOn = next
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.sm)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flashlight),
                    contentDescription = stringResource(
                        if (torchOn) R.string.scan_flashlight_on else R.string.scan_flashlight_off
                    ),
                    tint = torchTint
                )
            }
        }

        if (!useImageMode && scanning && matchedCode == null && !notFound) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        top = 24.dp,
                        end = 24.dp,
                        bottom = if (mode == ScannerMode.MULTI || scanConfirmation == "photo") {
                            120.dp
                        } else {
                            24.dp
                        }
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_scan),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(
                        when {
                            mode == ScannerMode.MULTI -> R.string.scan_multi_hint
                            scanConfirmation == "photo" -> R.string.scan_photo_hint
                            else -> R.string.scan_hint
                        }
                    ),
                    color = Color.White
                )
            }
        }

        if (!useImageMode && scanning && matchedCode == null && !notFound &&
            (mode == ScannerMode.MULTI || scanConfirmation == "photo")
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        clearAnalysis()
                        triggerCapture(mode == ScannerMode.MULTI, emptyList(), null)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_scan),
                    contentDescription = stringResource(R.string.scan_shutter),
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = notFound,
            enter = slideInVertically(tween(250)) { it / 3 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                shape = HodoorShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(Space.lg)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                    modifier = Modifier.padding(Space.xl)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = notFoundMessage ?: stringResource(R.string.scan_not_found),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = {
                            viewModel.retry()
                            scanSession++
                        },
                        shape = HodoorShapes.button
                    ) {
                        Text(stringResource(R.string.scan_retry))
                    }
                }
            }
        }

        // Short white shutter flash while the still photo is captured.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = shutter.value))
        )

        snapshot?.let { photo ->
            SnapshotPicker(
                snapshot = photo,
                onPick = viewModel::onPickSnapshotItem,
                onDismiss = {
                    viewModel.closeSnapshot()
                    scanSession++
                },
                animationsEnabled = animationsEnabled
            )
        }
    }
}

@Composable
private fun DetectionBoxes(
    detections: List<QrDetection>,
    modifier: Modifier = Modifier
) {
    if (detections.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.drawBehind {
        val stroke = Stroke(width = 4.dp.toPx())
        detections.forEach { detection ->
            val corners = detection.corners
            if (corners.size == 4) {
                for (index in 0 until 4) {
                    val start = corners[index]
                    val end = corners[(index + 1) % 4]
                    drawLine(
                        color = color,
                        start = Offset(start.first, start.second),
                        end = Offset(end.first, end.second),
                        strokeWidth = stroke.width
                    )
                }
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(detection.left.toFloat(), detection.top.toFloat()),
                    size = Size(
                        (detection.right - detection.left).toFloat(),
                        (detection.bottom - detection.top).toFloat()
                    ),
                    style = stroke
                )
            }
        }
    })
}

@Composable
private fun ScanFrame(
    modifier: Modifier = Modifier,
    success: Boolean = false,
    animationsEnabled: Boolean = true
) {
    val accent = MaterialTheme.colorScheme.primary
    val successColor = LocalHodoorColors.current.cause
    val successProgress by animateFloatAsState(
        targetValue = if (success) 1f else 0f,
        animationSpec = tween(150),
        label = "scanFrameColor"
    )
    val cornerColor = lerp(accent, successColor, successProgress)
    val frameScale by animateFloatAsState(
        targetValue = if (success) 1.04f else 1f,
        animationSpec = tween(150),
        label = "scanFrameScale"
    )
    val lineTransition = rememberInfiniteTransition(label = "scanLine")
    val lineProgress by lineTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    BoxWithConstraints(modifier = modifier) {
        val side = minOf(maxWidth, maxHeight) * SCAN_FRAME_FRACTION
        val left = (maxWidth - side) / 2
        val top = (maxHeight - side) / 2

        // Dim everything outside the frame.
        Box(
            modifier = Modifier.matchParentSize().drawWithCache {
                val dim = Color.Black.copy(alpha = 0.55f)
                onDrawBehind {
                    val topPx = top.toPx()
                    val leftPx = left.toPx()
                    val sidePx = side.toPx()
                    drawRect(dim, Offset.Zero, Size(size.width, topPx))
                    drawRect(
                        dim,
                        Offset(0f, topPx + sidePx),
                        Size(size.width, (size.height - topPx - sidePx).coerceAtLeast(0f))
                    )
                    drawRect(dim, Offset(0f, topPx), Size(leftPx, sidePx))
                    drawRect(
                        dim,
                        Offset(leftPx + sidePx, topPx),
                        Size((size.width - leftPx - sidePx).coerceAtLeast(0f), sidePx)
                    )
                }
            }
        )

        // Four rounded brackets plus the moving line.
        Box(
            modifier = Modifier
                .offset(x = left, y = top)
                .size(side)
                .graphicsLayer { scaleX = frameScale; scaleY = frameScale }
                .drawBehind {
                    val stroke = 4.dp.toPx()
                    val arm = size.minDimension * 0.18f
                    val w = size.width
                    val h = size.height
                    drawLine(cornerColor, Offset(0f, 0f), Offset(arm, 0f), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(0f, 0f), Offset(0f, arm), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(w, 0f), Offset(w - arm, 0f), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(w, 0f), Offset(w, arm), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(0f, h), Offset(arm, h), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(0f, h), Offset(0f, h - arm), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(w, h), Offset(w - arm, h), stroke, StrokeCap.Round)
                    drawLine(cornerColor, Offset(w, h), Offset(w, h - arm), stroke, StrokeCap.Round)
                    if (animationsEnabled) {
                        val y = lineProgress * h
                        drawLine(
                            color = cornerColor.copy(alpha = 0.85f),
                            start = Offset(stroke, y),
                            end = Offset(w - stroke, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
        )
    }
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(120)
    }
}
