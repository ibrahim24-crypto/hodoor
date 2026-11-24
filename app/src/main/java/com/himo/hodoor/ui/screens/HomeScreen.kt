package com.himo.hodoor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.himo.hodoor.R
import com.himo.hodoor.ui.theme.HodoorShapes
import com.himo.hodoor.ui.theme.Space
import com.himo.hodoor.ui.theme.animateCountUp
import com.himo.hodoor.ui.theme.pressScale
import com.himo.hodoor.ui.theme.rememberAnimationsEnabled
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    studentCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val slideUp = with(density) { 12.dp.toPx().toInt() }

    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            stage = 4
        } else {
            stage = 1
            delay(60)
            stage = 2
            delay(60)
            stage = 3
            delay(60)
            stage = 4
        }
    }

    val shownCount = animateCountUp(studentCount, 600, animationsEnabled)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(R.string.nav_home) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.nav_settings),
                            tint = MaterialTheme.colorScheme.onSurface
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
                .padding(Space.lg)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            AnimatedVisibility(
                visible = stage >= 1,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { slideUp }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    AppLogo(size = 64.dp)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = stage >= 2,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { slideUp }
            ) {
                ActionCard(
                    icon = painterResource(R.drawable.ic_qr_scan),
                    title = stringResource(R.string.home_scan),
                    subtitle = stringResource(R.string.home_scan_subtitle),
                    onClick = onScan
                )
            }

            AnimatedVisibility(
                visible = stage >= 3,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { slideUp }
            ) {
                ActionCard(
                    icon = painterResource(R.drawable.ic_search),
                    title = stringResource(R.string.home_search),
                    subtitle = stringResource(R.string.home_search_subtitle),
                    onClick = onSearch
                )
            }

            AnimatedVisibility(
                visible = stage >= 4,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { slideUp }
            ) {
                if (studentCount == 0) {
                    EmptyImportCard(onImport = onImport, animationsEnabled = animationsEnabled)
                } else {
                    SummaryCard(count = shownCount, onImport = onImport)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Card(
        shape = HodoorShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .pressScale(source)
    ) {
        Row(
            modifier = Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(count: Int, onImport: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        shape = HodoorShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                text = stringResource(R.string.home_students_loaded, count.toString()),
                style = MaterialTheme.typography.titleLarge
            )
            PillButton(
                label = stringResource(R.string.home_import_action),
                icon = painterResource(R.drawable.ic_file),
                onClick = onImport,
                interactionSource = source
            )
        }
    }
}

@Composable
private fun EmptyImportCard(onImport: () -> Unit, animationsEnabled: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled) {
            repeat(3) {
                pulse.animateTo(1.03f, tween(500))
                pulse.animateTo(1f, tween(500))
            }
        }
    }
    Card(
        shape = HodoorShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Space.xl).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_scan),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.home_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.sm))
            Box(
                modifier = Modifier
                    .pressScale(source)
                    .clip(HodoorShapes.button)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(interactionSource = source, indication = null, onClick = onImport)
                    .height(48.dp)
                    .graphicsLayerScale(pulse.value),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.home_import_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource
) {
    Box(
        modifier = Modifier
            .pressScale(interactionSource)
            .clip(HodoorShapes.button)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(horizontal = Space.lg)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    graphicsLayer { scaleX = scale; scaleY = scale }
