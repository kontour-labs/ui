package io.kontour.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * What something is saying about itself, from routine to urgent.
 *
 * One vocabulary for everything that carries a meaning in its colour — a
 * [io.kontour.ui.components.display.Banner], a [io.kontour.ui.components.display.Tag],
 * a toast. Each used to have an enum of its own with a different five or six of
 * the same words, so a tone chosen for one could not be handed to another, and a
 * neutral banner or an informational toast did not exist for no reason but that
 * nobody had added the entry.
 *
 * Every component takes all six. What a tone *looks* like is the component's
 * business — a toast is solid, a banner and a tag are tinted — but what it
 * *means* is the same everywhere.
 */
enum class Tone {
    /** No particular meaning: the surface's own tones. */
    Neutral,

    /** Something worth knowing. */
    Info,

    /** A note in the brand's colour — emphasis rather than a status. */
    Accent,

    /** Something went as it should. */
    Success,

    /** Something needs attention before it becomes a problem. */
    Warning,

    /** Something went wrong, or is about to be destroyed. */
    Danger,
}

/**
 * Every colour a component in [tone] is drawn with: the tone's own
 * [StatusColours], and for [Tone.Neutral] the surface's — the inverse surface
 * solid, as a toast is, and a sunken panel's ground with the ordinary ink and a
 * card's hairline as its container.
 *
 * One mapping for the banner, the event calendar's events and the coach marks,
 * so a tone means the same colours wherever it is handed.
 */
@Composable
@ReadOnlyComposable
internal fun toneColours(tone: Tone): StatusColours = when (tone) {
    Tone.Neutral -> Theme.colours.let {
        StatusColours(
            solid = it.surfaceInverse,
            onSolid = it.onSurfaceInverse,
            container = it.surfaceSunken,
            onContainer = it.content,
            border = it.outline,
        )
    }
    Tone.Info -> Theme.colours.info
    Tone.Success -> Theme.colours.success
    Tone.Warning -> Theme.colours.warning
    Tone.Danger -> Theme.colours.danger
    Tone.Accent -> Theme.colours.accent
}

/**
 * What text in [tone] is lettered in on its container: a tag's words, and a
 * word a sentence marks in a tone. The neutral's is the muted ink, so it reads
 * as a label rather than as something said.
 */
internal fun ColourScheme.toneInk(tone: Tone): Color = when (tone) {
    Tone.Neutral -> contentMuted
    Tone.Info -> info.onContainer
    Tone.Accent -> accent.onContainer
    Tone.Success -> success.onContainer
    Tone.Warning -> warning.onContainer
    Tone.Danger -> danger.onContainer
}
