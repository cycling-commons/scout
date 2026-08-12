package org.cyclingcommons.scout.karoo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.cyclingcommons.scout.karoo.R
import org.cyclingcommons.scout.karoo.session.ScoutSession
import org.cyclingcommons.scout.karoo.theme.ScoutKarooTheme

@Composable
fun MainScreen(onOpenTagging: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.main_subtitle),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.main_extension_ready),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.main_setup_steps),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text =
                stringResource(
                    R.string.main_ride_state,
                    ScoutSession.rideStateLabel(),
                ),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Button(
            onClick = onOpenTagging,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = stringResource(R.string.main_open_tagging))
        }
    }
}

@Preview(widthDp = 400, heightDp = 640)
@Composable
private fun MainScreenPreview() {
    ScoutKarooTheme {
        MainScreen(onOpenTagging = {})
    }
}
