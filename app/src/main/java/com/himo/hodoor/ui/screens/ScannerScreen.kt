package com.himo.hodoor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himo.hodoor.R
import com.himo.hodoor.scanner.QrAnalyzer
import com.himo.hodoor.scanner.SCAN_FRAME_FRACTION
import com.himo.hodoor.ui.settings.AppSettings
import java.util.concurrent.Executor

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onStudentFound: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val codes by viewModel.codes.collectAsStateWithLifecycle()
    val matchedCode by viewModel.matchedCode.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val imageError by viewModel.imageError.collectAsStateWithLifecycle()
    val developerMode by remember { mutableStateOf(AppSettings.imageScanEnabled(context)) }
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
    var torchOn by remember { mutableStateOf(false) }
    var scanSession by remember { mutableStateOf(0) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(matchedCode) {
        matchedCode?.let { code ->
            vibrate(context)
            onStudentFound(code)
        }
    }

    if (!useImageMode && !permissionGranted) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.scan_permission_required))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_grant))
                }
            }
        }
        return
    }

    DisposableEffect(
        permissionGranted, useImageMode, codes, scanSession, matchedCode, scanning, detected
    ) {
        if (useImageMode || !scanning || matchedCode != null || detected.isNotEmpty() ||
            codes.isEmpty()
        ) {
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
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val qrAnalyzer = QrAnalyzer { payloads -> viewModel.onScanResult(payloads) }
            analyzer = qrAnalyzer
            imageAnalysis.setAnalyzer(executor, qrAnalyzer)

            val groupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
            previewView.viewPort?.let { groupBuilder.setViewPort(it) }

            runCatching {
                camera = provider!!.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    groupBuilder.build()
                )
            }.onFailure { error ->
                Log.e("HodoorScanner", "Camera binding failed", error)
            }
        }, executor)

        onDispose {
            analyzer?.close()
            provider?.unbindAll()
            camera = null
        }
    }

    Box(
        modifier.fillMaxSize().then(
            if (useImageMode) Modifier.background(Color(0xFF101418)) else Modifier
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
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(R.string.scan_image_hint),
                    color = Color.White
                )
                Button(onClick = { imageLauncher.launch("image/*") }) {
                    Text(stringResource(R.string.scan_image_pick))
                }
                imageError?.let { Text(text = it, color = Color(0xFFFFB300)) }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (scanning && detected.isEmpty() && !notFound) {
                ScanFrame(Modifier.fillMaxSize())
            }
        }

        AppLogo(
            size = 40.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        )

        if (developerMode) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
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
                tint = Color.White
            )
        }

        if (!useImageMode) {
            IconButton(
                onClick = {
                    val next = !torchOn
                    runCatching { camera?.cameraControl?.enableTorch(next) }
                    torchOn = next
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flashlight),
                    contentDescription = stringResource(
                        if (torchOn) R.string.scan_flashlight_on else R.string.scan_flashlight_off
                    ),
                    tint = if (torchOn) Color(0xFFFFB300) else Color.White
                )
            }
        }

        if (!useImageMode && scanning && detected.isEmpty() && !notFound) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_scan),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(text = stringResource(R.string.scan_hint), color = Color.White)
            }
        }

        if (notFound) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.scan_not_found), color = Color.White)
                Button(onClick = {
                    viewModel.retry()
                    scanSession++
                }) {
                    Text(stringResource(R.string.scan_retry))
                }
            }
        }

        if (detected.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_picker_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.scan_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        detected.forEach { candidate ->
                            Button(
                                onClick = { viewModel.pickCandidate(candidate) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = candidate.studentName
                                            ?: stringResource(R.string.scan_unknown_code)
                                    )
                                    Text(
                                        text = candidate.code,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.scan_rescan))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val dim = Color.Black.copy(alpha = 0.55f)
            val side = minOf(size.width, size.height) * SCAN_FRAME_FRACTION
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f

            onDrawBehind {
                drawRect(dim, Offset.Zero, Size(size.width, top))
                drawRect(dim, Offset(0f, top + side), Size(size.width, size.height - top - side))
                drawRect(dim, Offset(0f, top), Size(left, side))
                drawRect(dim, Offset(left + side, top), Size(size.width - left - side, side))
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(side, side),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
    )
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
