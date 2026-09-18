package org.cyclingcommons.scout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.recording.RideFile
import org.cyclingcommons.scout.recording.RideShareTarget
import org.cyclingcommons.scout.ui.components.ScoutPage
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing
import java.text.DateFormat
import java.util.Date

@Composable
fun RidesScreen(
    rides: List<RideFile>,
    atlasName: String?,
    atlasReviewUrl: String?,
    onShareRide: (RideFile, RideShareTarget) -> Unit,
    onDeleteRide: (RideFile) -> Unit,
    onOpenRidePhotos: (RideFile) -> Unit,
    onBack: () -> Unit,
) {
    var sharing by remember { mutableStateOf<RideFile?>(null) }
    ScoutPage(
        title = stringResource(R.string.settings_rides),
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
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.settings_rides_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = ScoutColors.TextSecondary,
            )
            if (rides.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_rides_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextSecondary,
                )
            } else {
                Column {
                    rides.forEachIndexed { index, ride ->
                        if (index > 0) HorizontalDivider(color = ScoutColors.Outline)
                        RideRow(
                            ride = ride,
                            onShare = {
                                if (atlasReviewUrl != null && atlasName != null) {
                                    sharing = ride
                                } else {
                                    onShareRide(ride, RideShareTarget.FILE)
                                }
                            },
                            onDelete = { onDeleteRide(ride) },
                            onOpenPhotos = { onOpenRidePhotos(ride) },
                        )
                    }
                }
            }
        }
    }
    val rideToShare = sharing
    if (rideToShare != null && atlasName != null) {
        ShareRideDialog(
            atlasName = atlasName,
            onAtlas = {
                sharing = null
                onShareRide(rideToShare, RideShareTarget.ATLAS)
            },
            onFile = {
                sharing = null
                onShareRide(rideToShare, RideShareTarget.FILE)
            },
            onDismiss = { sharing = null },
        )
    }
}

@Composable
private fun RideRow(
    ride: RideFile,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhotos: () -> Unit,
) {
    val meta = stringResource(
        R.string.settings_ride_meta,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(ride.modifiedMs)),
        formatRideSize(ride.sizeBytes),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ScoutSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (ride.photoCount > 0) {
                        Modifier.clickable(onClick = onOpenPhotos)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = ride.name,
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = ScoutColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ride.photoCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_ride_photo_count,
                        ride.photoCount,
                        ride.photoCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoutColors.Brand,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.settings_share),
                tint = ScoutColors.TextSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.settings_delete),
                tint = ScoutColors.Brand,
            )
        }
    }
}

private fun formatRideSize(bytes: Long): String = "%.1f KB".format(bytes / 1024.0)
