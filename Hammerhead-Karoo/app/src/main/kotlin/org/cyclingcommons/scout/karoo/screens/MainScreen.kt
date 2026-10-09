package org.cyclingcommons.scout.karoo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.cyclingcommons.scout.karoo.R
import org.cyclingcommons.scout.karoo.session.ScoutSession
import org.cyclingcommons.scout.karoo.theme.ScoutKarooTheme
import org.cyclingcommons.scout.karoo.ui.KarooPreview
import org.cyclingcommons.scout.karoo.ui.ScoutKarooColors
import org.cyclingcommons.scout.karoo.ui.ScoutSpacing

/**
 * Launcher page. Sized for the Karoo's small screen: content starts at the top,
 * the Open tagging button sits above the fold, and the setup text scrolls.
 */
@Composable
fun MainScreen(onOpenTagging: () -> Unit) {
    val rideState by produceState(ScoutSession.rideStateLabel()) {
        while (true) {
            value = ScoutSession.rideStateLabel()
            delay(1000)
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScoutSpacing.lg, vertical = ScoutSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = ScoutKarooColors.Brand,
        )
        Text(
            text = stringResource(R.string.main_ride_state, rideState),
            style = MaterialTheme.typography.labelLarge,
            color = ScoutKarooColors.TextSecondary,
        )
        Button(
            onClick = onOpenTagging,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
        ) {
            Text(
                text = stringResource(R.string.main_open_tagging),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(R.string.main_subtitle),
            modifier = Modifier.padding(top = ScoutSpacing.sm),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.main_extension_ready),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.main_setup_steps),
            style = MaterialTheme.typography.bodySmall,
            color = ScoutKarooColors.TextSecondary,
        )
    }
}

@KarooPreview
@Composable
private fun MainScreenPreview() {
    ScoutKarooTheme {
        MainScreen(onOpenTagging = {})
    }
}
