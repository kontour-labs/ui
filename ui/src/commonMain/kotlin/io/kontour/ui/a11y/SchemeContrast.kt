package io.kontour.ui.a11y

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.ContrastLevel

/**
 * One foreground/background pairing that does not clear its threshold.
 *
 * [pair] reads the way a reviewer would say it — `"contentMuted on surface"` —
 * because the point of a failure is to name the token to change, and a token
 * name is what a palette is edited by.
 */
@Immutable
data class ContrastFailure(
    val pair: String,
    val ratio: Float,
    val required: Float,
) {
    override fun toString(): String =
        "$pair: ${format(ratio)}:1, needs ${format(required)}:1"
}

/**
 * Every pairing in [scheme] that a component could draw and that fails [tier].
 *
 * **Contrast is not something you can eyeball.** Three of the values originally
 * proposed for the built-in schemes looked fine and failed by a tenth of a
 * point. This is the walk that caught them, and it is public because
 * `theming.md` used to end its advice on custom palettes with "the library's own
 * contrast test is a good template" — which is a suggestion to copy ninety lines
 * out of a source set a consumer cannot see. It is one call now, and a theme of
 * your own can be gated by the same check that gates ours.
 *
 * ### What is deliberately not walked
 *
 * - `contentDisabled`, `outline`, `outlineSubtle` and the status `border`
 *   tones. WCAG 1.4.3 exempts disabled controls and 1.4.11 exempts purely
 *   decorative rules; holding them to a ratio forces dividers so dark they read
 *   as borders.
 * - `brand`, which exists *because* it cannot pass in light mode. That is the
 *   token's documented contract — see [brandIsSafeForText] for the question you
 *   should ask about it instead.
 * - The overlay washes and `scrim`. They composite over whatever is behind
 *   them, so there is no pairing to take a ratio of.
 *
 * ### What it does walk that is not a foreground on a background
 *
 * Two. [ColourScheme.outlineStrong] against the *fills* it has to bound, as
 * well as against the grounds — a tint that cannot separate itself has to be
 * bounded, and this is the promise that makes bounding it possible. And
 * [ColourScheme.surfaceTrack], which is a ground but not one of the four, so it
 * is walked against only the two foregrounds that actually land on it. Both
 * have the reason written at the walk.
 *
 * ### The one thing this cannot promise
 *
 * **A role added to [ColourScheme] owes this function nothing automatically.**
 * The lists below are written by hand, so a new colour arrives unchecked and
 * passing. Nothing can infer which ground a new token is drawn on, or whether it
 * is text; adding a role means adding it here. That was true when this was a
 * test and it is still true now that it is API — worth saying twice, because
 * "it is public so it must be complete" is the reasonable wrong assumption.
 */
fun contrastFailures(scheme: ColourScheme, tier: ContrastLevel): List<ContrastFailure> {
    val bodyText = when (tier) {
        ContrastLevel.Standard -> ContrastThreshold.BODY_TEXT
        ContrastLevel.High -> ContrastThreshold.BODY_TEXT_ENHANCED
    }
    val nonText = when (tier) {
        ContrastLevel.Standard -> ContrastThreshold.NON_TEXT
        ContrastLevel.High -> ContrastThreshold.LARGE_TEXT_ENHANCED
    }

    val failures = mutableListOf<ContrastFailure>()
    fun check(fgName: String, fg: Color, bgName: String, bg: Color, required: Float) {
        val ratio = contrastRatio(fg, bg)
        if (ratio < required) failures += ContrastFailure("$fgName on $bgName", ratio, required)
    }

    val grounds = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceSunken" to scheme.surfaceSunken,
        "surfaceRaised" to scheme.surfaceRaised,
    )

    // Text and control boundaries against every ground they can land on.
    for ((groundName, ground) in grounds) {
        check("content", scheme.content, groundName, ground, bodyText)
        check("contentMuted", scheme.contentMuted, groundName, ground, bodyText)
        check("contentSubtle", scheme.contentSubtle, groundName, ground, bodyText)
        check("outlineStrong", scheme.outlineStrong, groundName, ground, nonText)
        check("focusRing", scheme.focusRing, groundName, ground, nonText)
    }

    // A segmented control's labels, on the track they sit in.
    //
    // Deliberately not an entry in `grounds` above, which would walk all five
    // foregrounds against it. Only two of them land on a track — the selected
    // label is `content`, the unselected ones are `contentMuted` — and the
    // other three would be held to a ground nothing draws them on.
    //
    // The two that are excused are excused for a reason, not because they fail.
    // `contentSubtle` is never a segment label. And `outlineStrong` reads
    // 2.38:1 on the light track, which would have been a defect right up until
    // the thumb stopped being bounded: its 3:1-against-every-fill contract
    // exists so that a fill unable to separate itself can be given an edge, and
    // a track is the one ground in the scheme with nothing drawn around it. Put
    // `surfaceTrack` in `grounds` and you get a failure that describes a line
    // no component draws.
    //
    // What keeps the track honest instead is that `contentMuted` has to stay
    // legible on it, and at the enhanced tier that is 7:1 — which is what fixes
    // how dark the track may go. See `Palette.Grey300`.
    check("content", scheme.content, "surfaceTrack", scheme.surfaceTrack, bodyText)
    check("contentMuted", scheme.contentMuted, "surfaceTrack", scheme.surfaceTrack, bodyText)
    check("focusRing", scheme.focusRing, "surfaceTrack", scheme.surfaceTrack, nonText)

    // Source code, on the one ground it is ever drawn on.
    //
    // Highlighting is decorative, so these are held to body text on the ground
    // rather than to any separation from each other: a reader who cannot tell a
    // keyword from a literal has lost nothing the characters do not still say.
    // What would be a real defect is a literal that is hard to read at all.
    val code = scheme.code
    for ((role, colour) in listOf(
        "code.plain" to code.plain,
        "code.keyword" to code.keyword,
        "code.literal" to code.literal,
        "code.comment" to code.comment,
    )) {
        check(role, colour, "surfaceSunken", scheme.surfaceSunken, bodyText)
    }

    // Labels on solid fills, and the fills themselves against the page.
    for ((toneName, solid, onSolid) in listOf(
        Triple("primary", scheme.primary, scheme.onPrimary),
        Triple("accent", scheme.accent.solid, scheme.accent.onSolid),
        Triple("success", scheme.success.solid, scheme.success.onSolid),
        Triple("warning", scheme.warning.solid, scheme.warning.onSolid),
        Triple("danger", scheme.danger.solid, scheme.danger.onSolid),
        Triple("info", scheme.info.solid, scheme.info.onSolid),
    )) {
        check("on$toneName", onSolid, toneName, solid, bodyText)
        check(toneName, solid, "background", scheme.background, nonText)
    }

    // Text on tinted containers.
    for ((containerName, container, onContainer) in listOf(
        Triple("accent.container", scheme.accent.container, scheme.accent.onContainer),
        Triple("successContainer", scheme.success.container, scheme.success.onContainer),
        Triple("warningContainer", scheme.warning.container, scheme.warning.onContainer),
        Triple("dangerContainer", scheme.danger.container, scheme.danger.onContainer),
        Triple("infoContainer", scheme.info.container, scheme.info.onContainer),
    )) {
        check("on$containerName", onContainer, containerName, container, bodyText)
    }

    check(
        "onSurfaceInverse", scheme.onSurfaceInverse,
        "surfaceInverse", scheme.surfaceInverse,
        bodyText,
    )

    // The boundary of a *fill*, which is the pairing this walk never had.
    //
    // Everything above uses the surface tokens only ever as backgrounds. They
    // were never paired against each other, so a selection indicator's fill
    // against the fill under it was checked by nothing, in any test, in any
    // module. `SurfaceLadderTest` holds that floor now — in `:ui`'s own tests
    // rather than here, because it is a house floor and this function is the
    // WCAG walk.
    //
    // The tokens below are the fills that are *bounded*, and a segmented
    // control's thumb is deliberately no longer among them: it separates on
    // fill, and `surfaceTrack` exists so that it can. What remains is
    // `accent.container` and the grounds — a selected chip is a tint 1.29:1
    // from the page, so what says it is selected is the border round it.
    //
    // A fill that cannot separate itself has to be bounded, and
    // [ColourScheme.outlineStrong] is the token whose documented job that is —
    // "the boundary of an interactive control; clears 3:1 against every ground".
    // It already did against the grounds, which are checked above. What was
    // never checked is the other side of the same border: the **fill** it has to
    // bound. A scheme whose `accent.container` drifts toward its own
    // `outlineStrong` leaves a nav indicator with a boundary on one side only,
    // and nothing here would have said so.
    //
    // This cannot see whether a component *draws* the edge — no walk over a
    // palette can. `IndicatorVisibilityTest` in `:ui-catalog` photographs one
    // and measures the step across it; the two halves are a pair.
    for ((fillName, fill) in listOf(
        "surface" to scheme.surface,
        "surfaceSunken" to scheme.surfaceSunken,
        "surfaceRaised" to scheme.surfaceRaised,
        "accent.container" to scheme.accent.container,
    )) {
        check("outlineStrong", scheme.outlineStrong, fillName, fill, nonText)
    }

    return failures
}

/** Two decimal places, because the interesting failures miss by a tenth. */
private fun format(value: Float): String {
    val hundredths = (value * 100).toInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
