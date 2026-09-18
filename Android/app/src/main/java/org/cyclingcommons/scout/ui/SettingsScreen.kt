package org.cyclingcommons.scout.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.ui.components.ScoutButton
import org.cyclingcommons.scout.ui.components.ScoutPage
import org.cyclingcommons.scout.ui.components.ScoutSection
import org.cyclingcommons.scout.ui.components.ScoutToggleRow
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing
import org.cyclingcommons.scout.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    imperial: Boolean,
    keepScreenOn: Boolean,
    themeMode: ThemeMode,
    radarLabel: String,
    onImperial: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onPairRadar: () -> Unit,
    onHelp: () -> Unit,
    onReplayIntro: () -> Unit,
    onBack: () -> Unit,
) {
    ScoutPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        titleColor = ScoutColors.Brand,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScoutSpacing.lg,
                    end = ScoutSpacing.lg,
                    bottom = ScoutSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xl),
        ) {
            ScoutSection(
                title = stringResource(R.string.settings_display),
                titleColor = ScoutColors.Brand,
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
                    ThemeMode.entries.forEach { mode ->
                        ChoiceChip(
                            label = stringResource(mode.label()),
                            selected = mode == themeMode,
                            onClick = { onThemeMode(mode) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_appearance_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScoutColors.TextSecondary,
                )
                HorizontalDivider(color = ScoutColors.Outline)
                Text(
                    text = stringResource(R.string.settings_units),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
                    ChoiceChip(
                        label = stringResource(R.string.settings_units_metric),
                        selected = !imperial,
                        onClick = { onImperial(false) },
                    )
                    ChoiceChip(
                        label = stringResource(R.string.settings_units_imperial),
                        selected = imperial,
                        onClick = { onImperial(true) },
                    )
                }
                HorizontalDivider(color = ScoutColors.Outline)
                ScoutToggleRow(
                    label = stringResource(R.string.settings_keep_screen_on),
                    hint = stringResource(R.string.settings_keep_screen_on_hint),
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
            }

            ScoutSection(
                title = stringResource(R.string.settings_radar),
                subtitle = stringResource(R.string.settings_radar_hint),
                titleColor = ScoutColors.Brand,
            ) {
                Text(
                    text = radarLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextPrimary,
                )
                ScoutButton(
                    label = stringResource(R.string.settings_radar_pair),
                    onClick = onPairRadar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ScoutSection(
                title = stringResource(R.string.settings_about),
                titleColor = ScoutColors.Brand,
            ) {
                ScoutButton(
                    label = stringResource(R.string.settings_help),
                    onClick = onHelp,
                    modifier = Modifier.fillMaxWidth(),
                )
                ScoutButton(
                    label = stringResource(R.string.settings_show_intro),
                    onClick = onReplayIntro,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = stringResource(R.string.settings_version, appVersion()),
                style = MaterialTheme.typography.bodySmall,
                color = ScoutColors.TextSecondary,
                modifier = Modifier.padding(start = ScoutSpacing.xs),
            )
        }
    }
}

@StringRes
private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_appearance_system
    ThemeMode.LIGHT -> R.string.settings_appearance_light
    ThemeMode.DARK -> R.string.settings_appearance_dark
}

/** Unselected has to look like a control rather than a label, in both appearances. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ScoutColors.Brand,
            selectedLabelColor = ScoutColors.TextOnBrand,
            labelColor = ScoutColors.TextPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ScoutColors.OutlineStrong,
            selectedBorderColor = ScoutColors.Brand,
        ),
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
}
