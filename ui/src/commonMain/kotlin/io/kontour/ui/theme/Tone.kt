package io.kontour.ui.theme

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
