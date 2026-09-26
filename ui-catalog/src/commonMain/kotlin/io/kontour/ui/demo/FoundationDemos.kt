package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Scrim
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.foundation.markdownText
import io.kontour.ui.foundation.richText
import io.kontour.ui.input.clearFocusOnTap
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone

// --- Foundation -----------------------------------------------------------

internal val TextDemo = ComponentDemo(slug = "text") {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text("displaySmall", style = Theme.typography.displaySmall)
        Text("titleMedium", style = Theme.typography.titleMedium)
        Text("bodyMedium — the default for prose", style = Theme.typography.bodyMedium)
        Text(
            "labelSmall, muted",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
        // The AnnotatedString overload is the whole reason there are two: a
        // route number in the accent's text colour inside a sentence, without a
        // second component and without breaking the line box.
        Text(
            richText {
                +"The "
                tone(Tone.Accent, "950")
                +" leaves in 4 minutes."
            },
            style = Theme.typography.bodyMedium,
        )
        // Every verb, and the same string read from Markdown, so a difference
        // between the two paths shows up here side by side.
        Text(
            richText {
                bold("Bold"); +", "; italic("italic"); +", "; code("code"); +", "
                strikethrough("struck"); +" and "; link("a link") { echo("Link followed") }
            },
            style = Theme.typography.bodyMedium,
        )
        Text(
            markdownText(
                "**Bold**, *italic*, `code`, ~~struck~~ and [a link](https://kontour.io)",
                onLinkClick = { echo("Link followed") },
            ),
            style = Theme.typography.bodyMedium,
        )
    }
}

internal val IconDemo = ComponentDemo(slug = "icon") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Tabler.Outline.Bus, contentDescription = "Bus routes")
        Icon(Tabler.Outline.Star, contentDescription = null, size = Theme.sizing.iconLarge)
        // Inside a coloured surface, to show the tint following content colour
        // rather than being passed in.
        Surface(containerColour = Theme.colours.primary, shape = Theme.shapes.small) {
            Box(Modifier.padding(Theme.spacing.sm)) {
                Icon(Tabler.Outline.Star, contentDescription = null)
            }
        }
    }
}

internal val SurfaceDemo = ComponentDemo(slug = "surface") {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        listOf(
            "surface" to Theme.colours.surface,
            "sunken" to Theme.colours.surfaceSunken,
            "primary" to Theme.colours.primary,
        ).forEach { (name, colour) ->
            Surface(
                containerColour = colour,
                shape = Theme.shapes.medium,
                shadow = Theme.elevation.low,
            ) {
                Column(Modifier.padding(Theme.spacing.md)) {
                    // No colour argument anywhere below: the surface set
                    // `LocalContentColour` and both children resolved against it.
                    Text(name, style = Theme.typography.labelMedium)
                    Icon(Tabler.Outline.Star, contentDescription = null)
                }
            }
        }
    }
}

internal val DividerDemo = ComponentDemo(slug = "divider") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text("Above", style = Theme.typography.bodyMedium)
        HorizontalDivider()
        Text("Below", style = Theme.typography.bodyMedium)
        Row(
            modifier = Modifier.height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Left", style = Theme.typography.bodyMedium)
            VerticalDivider(Modifier.height(24.dp))
            Text("Right", style = Theme.typography.bodyMedium)
        }
    }
}

private val scrimDim = Knob.Flag("Dimmed", initial = true)

internal val ScrimDemo = ComponentDemo(slug = "scrim", knobs = listOf(scrimDim)) {
    // `fraction` is a lambda rather than a Float: a sheet reads its own drag
    // offset through it every frame, so the dimming tracks the gesture without
    // the scrim recomposing.
    val fraction = if (this[scrimDim]) 1f else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(Theme.shapes.medium)
            .background(Theme.colours.surfaceSunken),
    ) {
        Column(Modifier.padding(Theme.spacing.md)) {
            Text("Perth Underground", style = Theme.typography.titleSmall)
            Text(
                "Content behind the scrim, so there is something to dim.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
        Scrim(fraction = { fraction }, onDismissRequest = { echo("Dismissed") })
    }
}

// --- Adaptive -------------------------------------------------------------

// --- Text editing ---------------------------------------------------------

private val clearFocusEnabled = Knob.Flag("Clears focus on tap", initial = true)

internal val ClearFocusOnTapDemo = ComponentDemo(
    slug = "modifier-clear-focus-on-tap",
    knobs = listOf(clearFocusEnabled),
) {
    val name = rememberTextFieldState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearFocusOnTap(this[clearFocusEnabled])
            .clip(Theme.shapes.medium)
            .background(Theme.colours.surfaceSunken)
            .padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        TextField(state = name, label = "Name")
        // The point of the demo is the part with nothing in it: focus the
        // field, then press here. With the knob off the caret stays put, which
        // is the comparison — a screenshot of either state looks identical, so
        // the knob is the only way this reads as a behaviour at all.
        Box(Modifier.fillMaxWidth().height(72.dp))
        Text(
            "Focus the field, then press the empty space below it.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val SelectionIndicatorDemo = ComponentDemo(slug = "selection-indicator") {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Departures", "Route map", "Alerts")
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        TabBar(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = selected == index, onClick = { selected = index }, key = index) {
                    +label
                }
            }
        }
        Text(
            "The pill travels between tabs rather than appearing on one — one " +
                "indicator owned by the bar, not three owned by the tabs.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val foundationDemos = listOf(
    ClearFocusOnTapDemo,
    TextDemo,
    IconDemo,
    SurfaceDemo,
    DividerDemo,
    ScrimDemo,
    SelectionIndicatorDemo,
)
