package org.cyclingcommons.scout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.RideUiModel
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.Tile
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.UiMode
import org.cyclingcommons.scout.ui.components.ScoutButton
import org.cyclingcommons.scout.ui.components.ScoutLogo
import org.cyclingcommons.scout.ui.components.StatusPill
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutDimens
import org.cyclingcommons.scout.ui.theme.ScoutSpacing
import org.cyclingcommons.scout.ui.theme.ScoutType
import kotlin.math.ceil

private const val GRID_COLUMNS = 2

private val hideFromA11yTree = Modifier.semantics { invisibleToUser() }

private enum class TileCountdownKind { None, Undo, Confirm }

@Composable
fun ScoutRideScreen(
    model: RideUiModel,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onTileTap: (Int) -> Unit,
    onEndOpenSurface: () -> Unit,
    onRetryRadar: () -> Unit,
    onShareFit: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val scout = model.scout
    val snackbarHostState = remember { SnackbarHostState() }
    val startLabel = stringResource(R.string.ride_action_start)
    val resumeLabel = stringResource(R.string.ride_action_resume)
    LaunchedEffect(model.userMessage) {
        val message = model.userMessage ?: return@LaunchedEffect
        val actionLabel = when (scout.timer) {
            TimerState.IDLE -> startLabel
            TimerState.PAUSED -> resumeLabel
            TimerState.RUNNING -> null
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            when (scout.timer) {
                TimerState.IDLE -> onStart()
                TimerState.PAUSED -> onResume()
                TimerState.RUNNING -> Unit
            }
        }
        onDismissMessage()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScoutColors.Screen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScoutSpacing.md, vertical = ScoutSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
        ) {
        RideHeader(
            timer = scout.timer,
            elapsedSec = model.elapsedSec,
            tagCount = scout.tagTotal,
            hasLocationPermission = model.hasLocationPermission,
            fixLabel = model.fixLabel,
            lastFitPath = model.lastFitPath,
            onShareFit = onShareFit,
            onSettings = onSettings,
            onHelp = onHelp,
        )
        TagGrid(
            mode = scout.mode,
            tiles = scout.tiles,
            counts = scout.tileCounts,
            flashIdx = scout.flashIdx,
            flashUntilMs = scout.flashUntilMs,
            flashUndoWindow = scout.flashUndoWindow,
            pendingIdx = scout.pendingIdx,
            pendingUntilMs = scout.pendingUntilMs,
            title = scout.title,
            openSurfaceLabel = scout.openSurfaceLabel,
            onTileTap = onTileTap,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        scout.openSurfaceLabel?.let { label ->
            OpenSurfaceBanner(label = label, onEnd = onEndOpenSurface)
        }
        RadarStrip(
            live = scout.radarLive,
            hasRadar = model.radar.hasSavedRadar,
            recording = scout.timer == TimerState.RUNNING,
            seeking = model.radar.seeking,
            carCount = scout.carCount,
            speedKph = scout.lastCarSpeedKph,
            imperial = scout.imperial,
            onRetry = onRetryRadar,
        )
        RideControls(
            timer = scout.timer,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
        )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = ScoutSpacing.md,
                    end = ScoutSpacing.md,
                    bottom = ScoutDimens.controlHeight + 88.dp,
                ),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ScoutColors.SurfaceRaised,
                contentColor = ScoutColors.TextPrimary,
                actionColor = ScoutColors.Brand,
            )
        }
    }
}

@Composable
private fun RideHeader(
    timer: TimerState,
    elapsedSec: Long,
    tagCount: Int,
    hasLocationPermission: Boolean,
    fixLabel: String?,
    lastFitPath: String?,
    onShareFit: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    val timerLabel = stringResource(
        when (timer) {
            TimerState.IDLE -> R.string.ride_state_idle
            TimerState.RUNNING -> R.string.ride_state_recording
            TimerState.PAUSED -> R.string.ride_state_paused
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoutLogo(markSize = 48.dp)
            }
            Spacer(Modifier.weight(1f))
            StatusPill(
                label = timerLabel,
                dotColor = if (timer == TimerState.RUNNING) {
                    ScoutColors.Recording
                } else {
                    ScoutColors.Brand
                },
                modifier = Modifier.semantics {
                    contentDescription = timerLabel
                },
            )
            if (timer == TimerState.IDLE && lastFitPath != null) {
                IconButton(onClick = onShareFit) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.ride_action_share),
                        tint = ScoutColors.TextSecondary,
                    )
                }
            }
            IconButton(onClick = onHelp) {
                Icon(
                    imageVector = Icons.Filled.Help,
                    contentDescription = stringResource(R.string.ride_action_help),
                    tint = ScoutColors.TextSecondary,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.ride_action_settings),
                    tint = ScoutColors.TextSecondary,
                )
            }
        }
        val metricsA11y = stringResource(
            R.string.a11y_ride_status,
            timerLabel,
            formatElapsed(elapsedSec),
            pluralStringResource(R.plurals.a11y_ride_tag_total, tagCount, tagCount),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScoutColors.Surface, RoundedCornerShape(ScoutDimens.cardCorner))
                .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md)
                .semantics(mergeDescendants = true) {
                    contentDescription = metricsA11y
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metric(
                label = stringResource(R.string.ride_clock_label),
                value = formatElapsed(elapsedSec),
                style = ScoutType.rideClock,
            )
            Spacer(Modifier.weight(1f))
            Metric(
                label = stringResource(R.string.ride_tags_label),
                value = tagCount.toString(),
                style = ScoutType.metric,
                alignEnd = true,
            )
        }
        Text(
            text = when {
                !hasLocationPermission -> stringResource(R.string.ride_permission_needed)
                fixLabel != null -> fixLabel
                timer != TimerState.IDLE -> stringResource(R.string.ride_waiting_for_fix)
                lastFitPath != null -> stringResource(
                    R.string.ride_last_file,
                    lastFitPath.substringAfterLast('/'),
                )
                else -> stringResource(R.string.ride_waiting_for_fix)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (hasLocationPermission) {
                ScoutColors.TextSecondary
            } else {
                ScoutColors.Warning
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = ScoutSpacing.xs),
        )
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    style: TextStyle,
    alignEnd: Boolean = false,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        Text(
            text = label.uppercase(),
            style = ScoutType.overline,
            color = ScoutColors.TextSecondary,
        )
        Text(text = value, style = style, color = ScoutColors.TextPrimary)
    }
}

@Composable
private fun RideControls(
    timer: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = {
                Text(
                    text = stringResource(R.string.ride_stop_confirm_title),
                    color = ScoutColors.TextPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ride_stop_confirm_message),
                    color = ScoutColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        onStop()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.ride_stop_confirm_action),
                        color = ScoutColors.Brand,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(
                        text = stringResource(R.string.ride_action_cancel),
                        color = ScoutColors.TextPrimary,
                    )
                }
            },
            containerColor = ScoutColors.Surface,
            titleContentColor = ScoutColors.TextPrimary,
            textContentColor = ScoutColors.TextSecondary,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
    ) {
        when (timer) {
            TimerState.IDLE -> ScoutButton(
                label = stringResource(R.string.ride_action_start),
                onClick = onStart,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            TimerState.RUNNING -> {
                ScoutButton(
                    label = stringResource(R.string.ride_action_pause),
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                )
                ScoutButton(
                    label = stringResource(R.string.ride_action_stop),
                    onClick = { confirmStop = true },
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
            TimerState.PAUSED -> {
                ScoutButton(
                    label = stringResource(R.string.ride_action_resume),
                    onClick = onResume,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                ScoutButton(
                    label = stringResource(R.string.ride_action_stop),
                    onClick = { confirmStop = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * SPEC §7.1: an open stretch must keep nagging, and the reminder itself ends it.
 * The pulse is a slow two-blink every 15 s rather than a continuous animation.
 */
@Composable
private fun OpenSurfaceBanner(label: String, onEnd: () -> Unit) {
    var dimmed by remember(label) { mutableStateOf(false) }
    LaunchedEffect(label) {
        while (true) {
            delay(15_000)
            repeat(2) {
                dimmed = true
                delay(160)
                dimmed = false
                delay(160)
            }
        }
    }
    val endA11y = stringResource(R.string.a11y_surface_end_button, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScoutDimens.tileCorner))
            .background(if (dimmed) ScoutColors.BrandDim else ScoutColors.Brand)
            .clickable(role = Role.Button, onClick = onEnd)
            .semantics(mergeDescendants = true) {
                contentDescription = endA11y
                onClick {
                    onEnd()
                    true
                }
            }
            .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.surface_open_banner, label),
            style = ScoutType.tileLabel.copy(fontSize = 18.sp),
            color = ScoutColors.TextOnBrand,
            modifier = Modifier
                .weight(1f)
                .then(hideFromA11yTree),
        )
        Text(
            text = stringResource(R.string.surface_open_action).uppercase(),
            style = ScoutType.overline,
            color = ScoutColors.TextOnBrand,
            modifier = hideFromA11yTree,
        )
    }
}

@Composable
private fun RadarStrip(
    live: Boolean,
    hasRadar: Boolean,
    recording: Boolean,
    seeking: Boolean,
    carCount: Int,
    speedKph: Int,
    imperial: Boolean,
    onRetry: () -> Unit,
) {
    val detail = when {
        live -> null
        hasRadar && !recording -> stringResource(R.string.radar_ready)
        hasRadar && recording && seeking -> stringResource(R.string.radar_connecting)
        else -> stringResource(R.string.radar_none)
    }
    val canRetry = hasRadar && recording && !live
    val radarA11y = when {
        live -> {
            val cars = pluralStringResource(R.plurals.radar_cars, carCount, carCount)
            if (speedKph >= 0) {
                val speed = if (imperial) {
                    stringResource(R.string.radar_speed_mph, (speedKph * MPH_PER_KPH).toInt())
                } else {
                    stringResource(R.string.radar_speed_kph, speedKph)
                }
                stringResource(R.string.a11y_radar_live_speed, cars, speed)
            } else {
                stringResource(R.string.a11y_radar_live, cars)
            }
        }
        canRetry -> stringResource(R.string.a11y_radar_retry_button, detail!!)
        else -> stringResource(R.string.a11y_radar_status, detail!!)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScoutDimens.cardCorner))
            .background(ScoutColors.Surface, RoundedCornerShape(ScoutDimens.cardCorner))
            .border(
                width = 1.dp,
                color = if (live) ScoutColors.Recording else ScoutColors.Outline,
                shape = RoundedCornerShape(ScoutDimens.cardCorner),
            )
            .then(
                if (canRetry) {
                    Modifier.clickable(role = Role.Button, onClick = onRetry)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = radarA11y
                if (live) {
                    liveRegion = LiveRegionMode.Polite
                }
                if (canRetry) {
                    onClick {
                        onRetry()
                        true
                    }
                }
            }
            .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (live) ScoutColors.Recording else ScoutColors.Outline,
                    shape = CircleShape,
                ),
        )
        Text(
            text = stringResource(R.string.radar_label).uppercase(),
            style = ScoutType.overline,
            color = ScoutColors.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutColors.TextSecondary,
                textAlign = TextAlign.End,
            )
        } else {
            Text(
                text = pluralStringResource(R.plurals.radar_cars, carCount, carCount),
                style = ScoutType.metric,
                color = ScoutColors.TextPrimary,
            )
            if (speedKph >= 0) {
                Text(
                    text = if (imperial) {
                        stringResource(R.string.radar_speed_mph, (speedKph * MPH_PER_KPH).toInt())
                    } else {
                        stringResource(R.string.radar_speed_kph, speedKph)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TagGrid(
    mode: UiMode,
    tiles: List<Tile>,
    counts: List<Int>,
    flashIdx: Int,
    flashUntilMs: Long,
    flashUndoWindow: Boolean,
    pendingIdx: Int,
    pendingUntilMs: Long,
    title: String?,
    openSurfaceLabel: String?,
    onTileTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needsTick =
        (flashIdx >= 0 && flashUntilMs > nowMs) || (pendingIdx >= 0 && pendingUntilMs > nowMs)
    LaunchedEffect(flashIdx, flashUntilMs, pendingIdx, pendingUntilMs) {
        nowMs = System.currentTimeMillis()
    }
    LaunchedEffect(needsTick, flashUntilMs, pendingUntilMs) {
        while (needsTick) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= flashUntilMs && (pendingUntilMs == 0L || nowMs >= pendingUntilMs)) break
            delay(100)
        }
        nowMs = System.currentTimeMillis()
    }
    val rows = ceil(tiles.size / GRID_COLUMNS.toDouble()).toInt().coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
    ) {
        if (title != null) {
            val pickerHeading = stringResource(R.string.a11y_picker_heading, title)
            Text(
                text = title,
                style = ScoutType.overline,
                color = ScoutColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        heading()
                        contentDescription = pickerHeading
                    },
            )
        }
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
            ) {
                repeat(GRID_COLUMNS) { col ->
                    val i = row * GRID_COLUMNS + col
                    if (i >= tiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                        return@repeat
                    }
                    val tile = tiles[i]
                    val lit = i == flashIdx || (mode != UiMode.GRID && i == pendingIdx)
                    val openStretch = openSurfaceLabel
                        ?.takeIf { mode == UiMode.GRID && tile.code == PoiType.SURFACE }
                    val untilMs = when {
                        i == flashIdx && flashUntilMs > nowMs -> flashUntilMs
                        mode != UiMode.GRID && i == pendingIdx && pendingUntilMs > nowMs ->
                            pendingUntilMs
                        else -> 0L
                    }
                    val countdownKind = when {
                        untilMs > 0L && i == flashIdx && flashUndoWindow ->
                            TileCountdownKind.Undo
                        untilMs > 0L && i == pendingIdx -> TileCountdownKind.Confirm
                        else -> TileCountdownKind.None
                    }
                    TagTile(
                        label = openStretch ?: tile.label,
                        overline = openStretch?.let { tile.label },
                        count = counts.getOrElse(i) { 0 },
                        countdownSec = if (untilMs > 0L) {
                            ((untilMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                        } else {
                            0
                        },
                        countdownKind = countdownKind,
                        rgb = tile.rgb,
                        filled = lit || openStretch != null,
                        onClick = { onTileTap(i) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagTile(
    label: String,
    overline: String?,
    count: Int,
    countdownSec: Int,
    countdownKind: TileCountdownKind,
    rgb: Int,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = tileContentDescription(
        label = label,
        overline = overline,
        count = count,
        countdownKind = countdownKind,
    )
    // Tile hues are normative (SPEC §6.1); only the treatment around them changes.
    val color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    // Unlit, the hue is a wash over the page, so page ink reads on it either way. Lit,
    // the hue *is* the background, and the pale ones cannot carry a white label.
    val content = when {
        !filled -> ScoutColors.TextPrimary
        color.luminance() > PALE_TILE_LUMINANCE -> ScoutColors.TextOnPale
        else -> ScoutColors.TextOnBrand
    }
    val shape = RoundedCornerShape(ScoutDimens.tileCorner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = ScoutColors.tileIdleAlpha))
            .border(width = 2.dp, color = color, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
                onClick {
                    onClick()
                    true
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScoutSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = ScoutType.overline,
                    color = content.copy(alpha = 0.75f),
                    modifier = hideFromA11yTree,
                )
            }
            Text(
                text = label,
                style = ScoutType.tileLabel,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = hideFromA11yTree,
            )
        }
        if (count > 0) {
            Text(
                text = count.toString(),
                style = ScoutType.tileCount,
                color = content,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = ScoutSpacing.md)
                    .then(hideFromA11yTree),
            )
        }
        if (countdownSec > 0) {
            Text(
                text = "${countdownSec}s",
                style = ScoutType.countdown,
                color = content,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = ScoutSpacing.md, vertical = ScoutSpacing.sm)
                    .then(hideFromA11yTree),
            )
        }
    }
}

@Composable
private fun tileContentDescription(
    label: String,
    overline: String?,
    count: Int,
    countdownKind: TileCountdownKind,
): String {
    val parts = mutableListOf<String>()
    if (overline != null) {
        parts += stringResource(R.string.a11y_tile_open_surface, label, overline)
    } else {
        parts += stringResource(R.string.a11y_tile_tag_button, label)
    }
    if (count > 0) {
        parts += pluralStringResource(R.plurals.a11y_tile_count, count, count)
    }
    when (countdownKind) {
        TileCountdownKind.Undo ->
            parts += stringResource(R.string.a11y_tile_undo)
        TileCountdownKind.Confirm ->
            parts += stringResource(R.string.a11y_tile_confirm)
        TileCountdownKind.None -> Unit
    }
    return parts.joinToString(", ")
}

/** Ride clock from elapsed seconds. */
private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Where white on the hue falls below 3:1 — the floor for large bold text. SAND sits well past it. */
private const val PALE_TILE_LUMINANCE = 0.3f
private const val MPH_PER_KPH = 0.621371
