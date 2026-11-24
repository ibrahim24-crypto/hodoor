package com.himo.hodoor.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himo.hodoor.R
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.ui.settings.AppSettings
import com.himo.hodoor.ui.settings.LocaleHelper
import com.himo.hodoor.ui.theme.DarkSurface
import com.himo.hodoor.ui.theme.LightSurface
import kotlin.math.hypot
import kotlinx.coroutines.launch

private data class ThemeReveal(
    val center: Offset,
    val radius: Float,
    val previousSurface: Color,
    val targetSurface: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val causes by viewModel.causes.collectAsStateWithLifecycle()
    var showCauseDialog by remember { mutableStateOf(false) }
    var editingCause by remember { mutableStateOf<Cause?>(null) }
    var causeText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val language = remember { LocaleHelper.language(context) }
    var theme by remember { mutableStateOf(AppSettings.theme(context)) }
    val systemDark = isSystemInDarkTheme()
    val windowSize = LocalWindowInfo.current.containerSize
    var themeButtonCenter by remember { mutableStateOf(Offset.Zero) }
    var reveal by remember { mutableStateOf<ThemeReveal?>(null) }
    val revealProgress = remember { Animatable(0f) }
    val revealPath = remember { Path() }
    val themeButtonScale = remember { Animatable(1f) }
    var scanConfirmation by remember { mutableStateOf(AppSettings.scanConfirmation(context)) }
    var afterImport by remember { mutableStateOf(AppSettings.afterImport(context)) }
    var showImportDoneDialog by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = viewModel.exportTo(context, uri)
            message = result.fold(
                onSuccess = { count ->
                    if (count == 0) {
                        context.getString(R.string.settings_export_empty)
                    } else {
                        context.getString(R.string.settings_export_done, count.toString())
                    }
                },
                onFailure = { context.getString(R.string.settings_error, it.message.orEmpty()) }
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = viewModel.restoreFrom(context, uri)
            message = result.fold(
                onSuccess = { count ->
                    if (AppSettings.afterImport(context) == "home") {
                        onHome()
                    } else {
                        importedCount = count
                        showImportDoneDialog = true
                    }
                    context.getString(R.string.settings_restore_done, count.toString())
                },
                onFailure = { context.getString(R.string.settings_error, it.message.orEmpty()) }
            )
        }
    }

    fun changeTheme() {
        if (reveal != null) return
        val next = when (theme) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        val targetDark = when (next) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        val center = themeButtonCenter
        val radius = maxOf(
            hypot(center.x, center.y),
            hypot(windowSize.width - center.x, center.y),
            hypot(center.x, windowSize.height - center.y),
            hypot(windowSize.width - center.x, windowSize.height - center.y)
        ).coerceAtLeast(1f)
        val previousDark = when (theme) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        AppSettings.setTheme(context, next)
        theme = next
        reveal = ThemeReveal(
            center = center,
            radius = radius,
            previousSurface = if (previousDark) DarkSurface else LightSurface,
            targetSurface = if (targetDark) DarkSurface else LightSurface
        )
        scope.launch {
            revealProgress.snapTo(0f)
            themeButtonScale.snapTo(0.82f)
            launch {
                themeButtonScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
            }
            revealProgress.animateTo(
                1f,
                tween(550, easing = CubicBezierEasing(0.1f, 0.8f, 0.2f, 1f))
            )
            (context as? Activity)?.recreate()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                reveal?.let { state ->
                    drawRect(color = state.previousSurface)
                    val radius = state.radius * revealProgress.value
                    revealPath.reset()
                    revealPath.addOval(
                        Rect(
                            left = state.center.x - radius,
                            top = state.center.y - radius,
                            right = state.center.x + radius,
                            bottom = state.center.y + radius
                        )
                    )
                    clipPath(revealPath) {
                        drawRect(color = state.targetSurface)
                    }
                }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (reveal == null) {
                MaterialTheme.colorScheme.surface
            } else {
                Color.Transparent
            },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (reveal == null) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        }
                    ),
                    title = { ScreenTitle(R.string.settings_title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { changeTheme() },
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = themeButtonScale.value
                                    scaleY = themeButtonScale.value
                                }
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    themeButtonCenter = Offset(
                                        position.x + coordinates.size.width / 2f,
                                        position.y + coordinates.size.height / 2f
                                    )
                                }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_theme),
                                contentDescription = stringResource(R.string.settings_theme)
                            )
                        }
                    }
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_causes))

            if (causes.isEmpty()) {
                Text(stringResource(R.string.settings_no_causes))
            } else {
                causes.forEach { cause ->
                    ListItem(
                        headlineContent = { Text(cause.label) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    editingCause = cause
                                    causeText = cause.label
                                    showCauseDialog = true
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit),
                                        contentDescription = stringResource(R.string.student_edit_record)
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteCause(cause) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = stringResource(R.string.student_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Button(
                onClick = {
                    editingCause = null
                    causeText = ""
                    showCauseDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.settings_add_cause))
            }

            Button(
                onClick = { exportLauncher.launch("hodoor-records.csv") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_export),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.settings_export))
            }

            Button(
                onClick = { restoreLauncher.launch(arrayOf("text/csv", "text/*", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.settings_import_csv))
            }

            message?.let { Text(it) }

            Text(stringResource(R.string.settings_language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.setLanguage(context, "fr")
                        (context as? Activity)?.recreate()
                    }
                ) {
                    Text(stringResource(R.string.settings_language_french))
                }
                Button(
                    onClick = {
                        viewModel.setLanguage(context, "ar")
                        (context as? Activity)?.recreate()
                    }
                ) {
                    Text(stringResource(R.string.settings_language_arabic))
                }
            }
            Text(
                text = stringResource(
                    if (language == "ar") R.string.settings_language_arabic
                    else R.string.settings_language_french
                )
            )

            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_scan_behavior),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scanConfirmation == "slow",
                    onClick = {
                        AppSettings.setScanConfirmation(context, "slow")
                        scanConfirmation = "slow"
                    },
                    label = { Text(stringResource(R.string.settings_scan_slow)) }
                )
                FilterChip(
                    selected = scanConfirmation == "photo",
                    onClick = {
                        AppSettings.setScanConfirmation(context, "photo")
                        scanConfirmation = "photo"
                    },
                    label = { Text(stringResource(R.string.settings_scan_photo)) }
                )
            }

            Text(
                text = stringResource(R.string.settings_after_import),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = afterImport == "ask",
                    onClick = {
                        AppSettings.setAfterImport(context, "ask")
                        afterImport = "ask"
                    },
                    label = { Text(stringResource(R.string.settings_after_import_ask)) }
                )
                FilterChip(
                    selected = afterImport == "home",
                    onClick = {
                        AppSettings.setAfterImport(context, "home")
                        afterImport = "home"
                    },
                    label = { Text(stringResource(R.string.settings_after_import_home)) }
                )
            }
            }
        }

        if (showCauseDialog) {
        AlertDialog(
            onDismissRequest = { showCauseDialog = false },
            title = {
                Text(
                    stringResource(
                        if (editingCause == null) R.string.settings_add_cause
                        else R.string.settings_edit_cause
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = causeText,
                    onValueChange = { causeText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_cause_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                IconButton(
                    onClick = {
                        val label = causeText.trim()
                        if (editingCause == null) viewModel.addCause(label)
                        else editingCause?.let { viewModel.updateCause(it, label) }
                        showCauseDialog = false
                    },
                    enabled = causeText.isNotBlank()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.student_save)
                    )
                }
            },
            dismissButton = {
                IconButton(onClick = { showCauseDialog = false }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.student_cancel)
                    )
                }
            }
        )
        }

        if (showImportDoneDialog) {
            AlertDialog(
                onDismissRequest = { showImportDoneDialog = false },
                title = { Text(stringResource(R.string.settings_restore_done, importedCount.toString())) },
                text = {
                    Text(
                        stringResource(
                            R.string.settings_import_done_question,
                            importedCount.toString()
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showImportDoneDialog = false
                            restoreLauncher.launch(arrayOf("text/csv", "text/*", "*/*"))
                        }
                    ) {
                        Text(stringResource(R.string.settings_import_more))
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showImportDoneDialog = false
                        onHome()
                    }) {
                        Text(stringResource(R.string.nav_home))
                    }
                }
            )
        }
    }
}
