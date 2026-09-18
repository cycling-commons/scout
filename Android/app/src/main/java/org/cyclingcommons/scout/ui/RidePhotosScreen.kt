package org.cyclingcommons.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.recording.RidePhoto
import org.cyclingcommons.scout.ui.components.ScoutPage
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutDimens
import org.cyclingcommons.scout.ui.theme.ScoutSpacing
import java.text.DateFormat
import java.util.Date

@Composable
fun RidePhotosScreen(
    rideName: String,
    photos: List<RidePhoto>,
    onBack: () -> Unit,
) {
    ScoutPage(
        title = stringResource(R.string.settings_ride_photos_title),
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
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.lg),
        ) {
            Text(
                text = rideName,
                style = MaterialTheme.typography.bodyMedium,
                color = ScoutColors.TextSecondary,
            )
            if (photos.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_ride_photos_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextSecondary,
                )
            } else {
                photos.forEach { photo ->
                    PhotoRow(photo)
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(photo: RidePhoto) {
    val whenLabel = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(photo.capturedAtMs))
    val place = if (photo.lat != null && photo.lon != null) {
        stringResource(R.string.settings_ride_photo_gps, photo.lat, photo.lon)
    } else {
        stringResource(R.string.settings_ride_photo_no_gps)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = photo.file,
            contentDescription = photo.caption.ifBlank {
                stringResource(R.string.ride_photo_preview)
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ScoutDimens.photoThumb)
                .clip(RoundedCornerShape(ScoutDimens.tileCorner)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xs)) {
            Text(
                text = photo.caption.ifBlank { stringResource(R.string.settings_ride_photo_no_caption) },
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutColors.TextPrimary,
            )
            Text(
                text = whenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = ScoutColors.TextSecondary,
            )
            Text(
                text = place,
                style = MaterialTheme.typography.bodySmall,
                color = ScoutColors.TextSecondary,
            )
        }
    }
}
