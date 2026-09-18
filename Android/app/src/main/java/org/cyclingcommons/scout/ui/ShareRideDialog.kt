package org.cyclingcommons.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.ui.components.ScoutButton
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing

@Composable
fun ShareRideDialog(
    atlasName: String,
    onAtlas: () -> Unit,
    onFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.share_ride_title),
                color = ScoutColors.TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {
                Text(
                    text = stringResource(R.string.share_ride_hint, atlasName),
                    color = ScoutColors.TextSecondary,
                )
                Column(
                    modifier = Modifier.padding(top = ScoutSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm),
                ) {
                    ScoutButton(
                        label = atlasName,
                        onClick = onAtlas,
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ScoutButton(
                        label = stringResource(R.string.share_other_apps),
                        onClick = onFile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
