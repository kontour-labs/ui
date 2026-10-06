package io.kontour.ui.catalog.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.display.Tag
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme

/**
 * The gallery's apps, each a card with a picture, what it is, the components
 * it is the best demo of, and a button that runs it in place of the gallery.
 *
 * As many side by side as there is room for at a phone's width each, one above
 * the other when not even two fit.
 */
@Composable
internal fun AppsPage(modifier: Modifier = Modifier) {
    val open = LocalOpenApp.current
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = ScrollDefaults.flingBehavior())) {
        Column(
            Modifier.fillMaxWidth().padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Text(
                "Three small apps built from the library, each with its own navigation, sheets and back stack. " +
                    "They take the whole window while they run; back at an app's first page, or its close " +
                    "button, comes back here.",
                style = Theme.typography.bodyMedium,
                colour = Theme.colours.contentMuted,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val apps = MiniApp.entries
                val gap = Theme.spacing.md
                // As many to a row as fit a phone wide, and the rows filled from
                // the start: three on a desktop, two and one on a tablet.
                val across = ((maxWidth + gap) / (AppCardWidth + gap)).toInt().coerceIn(1, apps.size)
                if (across > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in apps.chunked(across)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (app in row) AppCard(app, onOpen = { open(app) }, modifier = Modifier.weight(1f))
                                repeat(across - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (app in apps) AppCard(app, onOpen = { open(app) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: MiniApp, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.widthIn(max = AppCardMaxWidth).testTag("open-${app.name}"),
        variant = CardVariant.Outlined,
        contentPadding = PaddingValues(0.dp),
    ) {
        when (app) {
            MiniApp.Transit -> SkylineArt(Modifier.fillMaxWidth().height(AppArtHeight))
            MiniApp.Messages -> ConversationArt(Modifier.fillMaxWidth().height(AppArtHeight))
            MiniApp.Board -> BoardArt(Modifier.fillMaxWidth().height(AppArtHeight))
        }
        Column(
            Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                Icon(app.icon, contentDescription = null)
                Text(app.title, style = Theme.typography.titleMedium)
            }
            Text(app.summary, style = Theme.typography.bodyMedium, colour = Theme.colours.contentMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            ) {
                for (component in app.components) Tag { +component }
            }
            Button(onClick = onOpen) { +"Open ${app.title}" }
        }
    }
}

/** The narrowest a card is drawn beside another: about a phone's width. */
private val AppCardWidth = 320.dp

/** The widest a card is drawn, so a sidebar's window does not stretch one into a banner. */
private val AppCardMaxWidth = 560.dp

private val AppArtHeight = 160.dp
