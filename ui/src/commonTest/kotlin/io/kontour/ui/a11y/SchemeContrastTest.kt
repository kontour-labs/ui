package io.kontour.ui.a11y

import androidx.compose.ui.graphics.Color
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.darkColourScheme
import io.kontour.ui.theme.lightColourScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That [contrastFailures] reports what a reviewer needs and nothing else.
 *
 * `ColourSchemeContrastTest` runs it over the four built-in schemes and asserts
 * the list is empty, which proves it does not fire falsely but says nothing
 * about whether it fires *at all*. These are the other half: a scheme with a
 * known-bad value in it, and the exemptions holding.
 */
class SchemeContrastTest {

    @Test
    fun aFailingTokenIsNamedWithItsGround() {
        // #767676 is 4.54:1 on white — the classic "looks fine, misses by a
        // hundredth" grey. Against `surfaceSunken` (#F6F6F6) it drops below 4.5.
        val failures = contrastFailures(
            lightColourScheme(contentMuted = Color(0xFF767676)),
            ContrastLevel.Standard,
        )

        assertTrue(
            failures.any { it.pair == "contentMuted on surfaceSunken" },
            "a muted grey that fails on the sunken ground was not reported. Got: $failures",
        )
        val reported = failures.first { it.pair == "contentMuted on surfaceSunken" }
        assertEquals(
            4.5f, reported.required,
            "body text on a ground is a 4.5:1 requirement at the standard tier",
        )
        assertTrue(
            reported.ratio < 4.5f,
            "the reported ratio ${reported.ratio} is not actually a failure",
        )
    }

    @Test
    fun theEnhancedTierIsStricterThanTheStandardOne() {
        // The same scheme, walked twice. Nothing about it changes except what is
        // asked of it, which is the whole meaning of a tier.
        val standard = contrastFailures(darkColourScheme(), ContrastLevel.Standard)
        val enhanced = contrastFailures(darkColourScheme(), ContrastLevel.High)

        assertTrue(
            standard.isEmpty(),
            "the built-in dark scheme fails its own tier: $standard",
        )
        assertTrue(
            enhanced.size > standard.size,
            "the standard dark scheme cleared the 7:1 tier as well, which means " +
                "the tier is not being applied — `contrastFailures` is reading " +
                "the same threshold for both",
        )
    }

    @Test
    fun theDocumentedExemptionsAreNotWalked() {
        // `brand` is allowed to fail by contract, and the decorative lines are
        // WCAG-exempt. Set all four to something that could not possibly pass
        // and assert the walk stays silent — otherwise the exemption is prose
        // rather than behaviour.
        val unreadable = Color(0xFF141414)
        val failures = contrastFailures(
            darkColourScheme(
                brand = unreadable,
                outline = unreadable,
                outlineSubtle = unreadable,
                contentDisabled = unreadable,
            ),
            ContrastLevel.Standard,
        )

        assertTrue(
            failures.isEmpty(),
            "an exempt token was walked. The exemptions are documented on " +
                "`contrastFailures` and have to hold, or every custom palette " +
                "gets told to fix a divider. Got: $failures",
        )
    }

    @Test
    fun aFailureReadsAsSomethingYouCouldAct(): Unit = assertEquals(
        "contentMuted on surface: 3.10:1, needs 4.50:1",
        ContrastFailure("contentMuted on surface", 3.1f, 4.5f).toString(),
        "a failure has to name the token to change and the number to beat",
    )
}
