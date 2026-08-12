package org.cyclingcommons.scout.karoo.tagging

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.ScoutUiState
import org.cyclingcommons.scout.domain.Tile
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.UiMode
import org.cyclingcommons.scout.karoo.R
import org.cyclingcommons.scout.karoo.ui.PALE_TILE_LUMINANCE
import org.cyclingcommons.scout.karoo.ui.ScoutDimens
import org.cyclingcommons.scout.karoo.ui.ScoutKarooColors
import org.cyclingcommons.scout.karoo.ui.ScoutSpacing
import org.cyclingcommons.scout.karoo.ui.ScoutType
import kotlin.math.ceil

private const val GRID_COLUMNS = 2

@Composable
fun TaggingScreen(
    model: TaggingUiModel,
    onTileTap: (Int) -> Unit,
    onEndOpenSurface: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val scout = model.scout
    val snackbarHostState = remember { SnackbarHostState() }
    val idleMessage = stringResource(R.string.tagging_idle_prompt)
    LaunchedEffect(model.riderMessage) {
        if (model.riderMessage == "idle") {
            snackbarHostState.showSnackbar(
                message = idleMessage,
                duration = SnackbarDuration.Long,
            )
            onDismissMessage()
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ScoutKarooColors.Screen),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScoutSpacing.md, vertical = ScoutSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
        ) {
            TaggingHeader(timer = scout.timer, tagTotal = scout.tagTotal)
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
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            )
            scout.openSurfaceLabel?.let { label ->
                OpenSurfaceBanner(label = label, onEnd = onEndOpenSurface)
            }
            RadarStrip(
                live = scout.radarLive,
                carCount = scout.carCount,
                speedKph = scout.lastCarSpeedKph,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(ScoutSpacing.md),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ScoutKarooColors.Surface,
                contentColor = ScoutKarooColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun TaggingHeader(timer: TimerState, tagTotal: Int) {
    val dotColor =
        if (timer == TimerState.RUNNING) {
            ScoutKarooColors.Recording
        } else {
            ScoutKarooColors.IdleDot
        }
    val stateLabel =
        stringResource(
            when (timer) {
                TimerState.RUNNING -> R.string.ride_state_recording
                TimerState.PAUSED -> R.string.ride_state_paused
                TimerState.IDLE -> R.string.ride_state_idle
            },
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = ScoutKarooColors.Brand,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.tagging_tag_count, tagTotal),
            style = ScoutType.overline,
            color = ScoutKarooColors.TextSecondary,
        )
        Spacer(Modifier.size(ScoutSpacing.sm))
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .background(dotColor, CircleShape),
        )
        Spacer(Modifier.size(ScoutSpacing.xs))
        Text(
            text = stateLabel.uppercase(),
            style = ScoutType.overline,
            color = ScoutKarooColors.TextSecondary,
        )
    }
}

@Composable
private fun RadarStrip(
    live: Boolean,
    carCount: Int,
    speedKph: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ScoutKarooColors.Surface, RoundedCornerShape(ScoutDimens.cardCorner))
                .border(1.dp, ScoutKarooColors.Outline, RoundedCornerShape(ScoutDimens.cardCorner))
                .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.radar_label).uppercase(),
            style = ScoutType.overline,
            color = ScoutKarooColors.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        if (!live) {
            Text(
                text = stringResource(R.string.radar_none),
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutKarooColors.TextSecondary,
            )
        } else {
            Text(
                text = stringResource(R.string.radar_cars, carCount),
                style = ScoutType.metric,
                color = ScoutKarooColors.TextPrimary,
            )
            if (speedKph >= 0) {
                Text(
                    text = stringResource(R.string.radar_speed_kph, speedKph),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutKarooColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun OpenSurfaceBanner(
    label: String,
    onEnd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScoutDimens.tileCorner))
                .background(ScoutKarooColors.Brand)
                .clickable(role = Role.Button, onClick = onEnd)
                .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.surface_open_banner, label),
            style = ScoutType.tileLabel.copy(fontSize = 18.sp),
            color = ScoutKarooColors.TextOnBrand,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.surface_open_action).uppercase(),
            style = ScoutType.overline,
            color = ScoutKarooColors.TextOnBrand,
        )
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
            Text(
                text = title,
                style = ScoutType.overline,
                color = ScoutKarooColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        repeat(rows) { row ->
            Row(
                modifier =
                    Modifier
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
                    val openStretch =
                        openSurfaceLabel?.takeIf { mode == UiMode.GRID && tile.code == PoiType.SURFACE }
                    val untilMs =
                        when {
                            i == flashIdx && flashUntilMs > nowMs -> flashUntilMs
                            mode != UiMode.GRID && i == pendingIdx && pendingUntilMs > nowMs ->
                                pendingUntilMs
                            else -> 0L
                        }
                    TagTile(
                        label = openStretch ?: tile.label,
                        overline = openStretch?.let { tile.label },
                        count = counts.getOrElse(i) { 0 },
                        countdownSec =
                            if (untilMs > 0L) {
                                ((untilMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                            } else {
                                0
                            },
                        rgb = tile.rgb,
                        filled = lit || openStretch != null,
                        onClick = { onTileTap(i) },
                        modifier =
                            Modifier
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
    rgb: Int,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    val content =
        when {
            !filled -> ScoutKarooColors.TextPrimary
            color.luminance() > PALE_TILE_LUMINANCE -> ScoutKarooColors.TextOnPale
            else -> ScoutKarooColors.TextOnBrand
        }
    val shape = RoundedCornerShape(ScoutDimens.tileCorner)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(
                    if (filled) color else color.copy(alpha = ScoutKarooColors.TileIdleAlpha),
                )
                .border(2.dp, color, shape)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
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
                )
            }
            Text(
                text = label,
                style = ScoutType.tileLabel,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (count > 0) {
            Text(
                text = count.toString(),
                style = ScoutType.tileCount,
                color = content,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = ScoutSpacing.md),
            )
        }
        if (countdownSec > 0) {
            Text(
                text = "${countdownSec}s",
                style = ScoutType.countdown,
                color = content,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = ScoutSpacing.md, vertical = ScoutSpacing.sm),
            )
        }
    }
}
