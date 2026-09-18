package org.cyclingcommons.scout.ui



import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import org.cyclingcommons.scout.R

import org.cyclingcommons.scout.help.HelpContent

import org.cyclingcommons.scout.help.HelpLink

import org.cyclingcommons.scout.help.HelpSection

import org.cyclingcommons.scout.ui.components.ScoutButton

import org.cyclingcommons.scout.ui.components.ScoutLogo

import org.cyclingcommons.scout.ui.components.ScoutLogoLayout

import org.cyclingcommons.scout.ui.theme.ScoutColors

import org.cyclingcommons.scout.ui.theme.ScoutSpacing

import org.cyclingcommons.scout.ui.theme.ScoutType

/** CC wordmark width as a fraction of the content area (25% smaller than full width). */
private const val CyclingCommonsLogoWidthFraction = 0.75f

/** Softens the SVG lockup’s square raster bounds on the intro hero. */
private val IntroLogoCornerRadius = 8.dp

/** First run only: what Scout does, what it costs, and who it belongs to. */

@Composable

fun IntroScreen(
    onContinue: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val legalSection = remember(context) {
        HelpContent.loadLegalSections(context).firstOrNull()
    }

    Column(

        modifier = Modifier

            .fillMaxSize()

            .background(ScoutColors.Screen)

            .verticalScroll(rememberScrollState())

            .padding(horizontal = ScoutSpacing.xl, vertical = ScoutSpacing.xxl),

        horizontalAlignment = Alignment.CenterHorizontally,

    ) {

        Spacer(Modifier.height(ScoutSpacing.lg))

        ScoutLogo(
            markSize = 88.dp,
            layout = ScoutLogoLayout.Vertical,
            lockupWidthFraction = 0.48f,
            modifier = Modifier.clip(RoundedCornerShape(IntroLogoCornerRadius)),
        )

        Spacer(Modifier.height(ScoutSpacing.md))

        Text(

            text = stringResource(R.string.brand_tagline),

            style = MaterialTheme.typography.bodyLarge,

            color = ScoutColors.TextSecondary,

            textAlign = TextAlign.Center,

        )

        Spacer(Modifier.height(ScoutSpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.lg)) {

            IntroPoint(stringResource(R.string.intro_point_tag))

            IntroPoint(stringResource(R.string.intro_point_photo))

            IntroPoint(stringResource(R.string.intro_point_radar))

            IntroPoint(stringResource(R.string.intro_point_private))

        }

        Spacer(Modifier.height(ScoutSpacing.xl))

        Text(

            text = stringResource(R.string.intro_permissions_title),

            style = MaterialTheme.typography.titleMedium,

            color = ScoutColors.TextPrimary,

            modifier = Modifier.fillMaxWidth(),

        )

        Spacer(Modifier.height(ScoutSpacing.sm))

        Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.md)) {

            IntroPoint(stringResource(R.string.intro_permission_location))

            IntroPoint(stringResource(R.string.intro_permission_notifications))

            IntroPoint(stringResource(R.string.intro_permission_bluetooth))

            IntroPoint(stringResource(R.string.intro_permission_camera))

        }

        Text(

            text = stringResource(R.string.intro_permissions_when),

            style = MaterialTheme.typography.bodyMedium,

            color = ScoutColors.TextSecondary,

            modifier = Modifier

                .fillMaxWidth()

                .padding(top = ScoutSpacing.md),

        )

        Spacer(Modifier.height(ScoutSpacing.xl))

        IntroLegalBlock(section = legalSection, onOpenLink = onOpenLink)

        Spacer(Modifier.height(ScoutSpacing.xxl))

        Spacer(Modifier.height(ScoutSpacing.lg))

        Text(

            text = stringResource(R.string.intro_powering).uppercase(),

            style = ScoutType.overline,

            color = ScoutColors.TextSecondary,

        )

        Spacer(Modifier.height(ScoutSpacing.md))

        Image(
            painter = painterResource(R.drawable.instance_logo),
            contentDescription = stringResource(R.string.cd_cycling_commons_logo),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(CyclingCommonsLogoWidthFraction),
        )

        Spacer(Modifier.height(ScoutSpacing.xxl))

        Text(
            text = stringResource(R.string.intro_legal_ack),
            style = MaterialTheme.typography.bodyMedium,
            color = ScoutColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ScoutSpacing.md))

        ScoutButton(

            label = stringResource(R.string.intro_continue),

            onClick = onContinue,

            primary = true,

            modifier = Modifier.fillMaxWidth(),

        )

        Spacer(Modifier.height(ScoutSpacing.lg))

    }

}



@Composable
private fun IntroLegalBlock(
    section: HelpSection?,
    onOpenLink: (String) -> Unit,
) {
    val title = section?.heading ?: stringResource(R.string.intro_legal_title)
    val body = section?.body.orEmpty()
    val links = section?.links.orEmpty().ifEmpty {
        listOf(
            HelpLink(
                label = stringResource(R.string.intro_legal_privacy),
                url = stringResource(R.string.intro_legal_privacy_url),
            ),
            HelpLink(
                label = stringResource(R.string.intro_legal_terms),
                url = stringResource(R.string.intro_legal_terms_url),
            ),
        )
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = ScoutColors.TextPrimary,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(ScoutSpacing.sm))

    Column(verticalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
        if (body.isEmpty()) {
            Text(
                text = stringResource(R.string.intro_legal_summary),
                style = MaterialTheme.typography.bodyLarge,
                color = ScoutColors.TextPrimary,
            )
        } else {
            body.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScoutColors.TextPrimary,
                )
            }
        }
        links.forEach { link ->
            IntroLegalLinkRow(link = link, onOpenLink = onOpenLink)
        }
    }
}

@Composable
private fun IntroLegalLinkRow(
    link: HelpLink,
    onOpenLink: (String) -> Unit,
) {
    TextButton(
        onClick = { onOpenLink(link.url) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = link.label,
            style = MaterialTheme.typography.bodyLarge,
            color = ScoutColors.Brand,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}



@Composable

private fun IntroPoint(text: String) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.md),

    ) {

        Spacer(

            modifier = Modifier

                .padding(top = ScoutSpacing.sm)

                .size(8.dp)

                .background(ScoutColors.Brand, CircleShape),

        )

        Text(

            text = text,

            style = MaterialTheme.typography.bodyLarge,

            color = ScoutColors.TextPrimary,

            modifier = Modifier.weight(1f),

        )

    }

}


