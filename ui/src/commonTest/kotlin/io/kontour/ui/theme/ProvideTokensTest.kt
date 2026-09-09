package io.kontour.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Changing one token for a subtree changes one token — and finishes the job.
 *
 * Two failures live here, and only the first is obvious.
 *
 * **A nested [KontourTheme] does not inherit.** Every parameter it is not given
 * re-runs its default, and those defaults read the *platform*, not the enclosing
 * theme. So a screen forcing dark mode inside a German app gets 47 English
 * strings back, and `theming.md` recommended exactly that pattern. That is the
 * defect [ProvideTokens] exists to make un-writable, and
 * [aNestedThemeStillSwallowsWhatItIsNotGiven] pins the old behaviour so the
 * claim in the KDoc stays true rather than becoming folklore.
 *
 * **The obvious hand-rolled replacement is worse, and silently.**
 * `CompositionLocalProvider(LocalColourScheme provides scheme)` puts the new
 * scheme in place and leaves `LocalContentColour` — which is *derived* from the
 * scheme where [KontourTheme] provides it — holding the old palette's content
 * colour. Every `Text` and `Icon` in the subtree keeps drawing in a colour from
 * a scheme that is no longer in force. Half the subtree changes, nothing
 * errors, and on two schemes of similar lightness nothing looks wrong either.
 * [theHandRolledVersionIsTheDefectThisFunctionRemoves] is that measurement.
 */
@OptIn(ExperimentalTestApi::class)
class ProvideTokensTest {

    @Test
    fun anArgumentNotPassedDoesNotChange() {
        val seen = readInside { inner ->
            ProvideTokens(colours = Other, content = inner)
        }
        assertEquals(
            German.confirm, seen.strings.confirm,
            "ProvideTokens was given a colour scheme and lost the strings. " +
                "Every parameter it is not passed must default to the value " +
                "already in scope, or it is a nested KontourTheme with a " +
                "different name.",
        )
        assertEquals(Tight.md, seen.spacing.md, "spacing was not inherited")
        assertEquals(Other.surface, seen.colours.surface, "the argument that was passed did not take")
    }

    @Test
    fun aNestedThemeStillSwallowsWhatItIsNotGiven() {
        // Not a wish: the recorded behaviour ProvideTokens exists because of.
        // If this ever starts failing, KontourTheme has learned to inherit and
        // ProvideTokens' KDoc — and theming.md — need rewriting rather than
        // this assertion flipping.
        val seen = readInside { inner ->
            KontourTheme(darkTheme = true, content = inner)
        }
        assertNotEquals(
            German.confirm, seen.strings.confirm,
            "a nested KontourTheme now inherits its enclosing theme's strings. " +
                "That is an improvement, but ProvideTokens' KDoc and theming.md " +
                "both explain at length that it does not.",
        )
        assertEquals(
            Strings().confirm, seen.strings.confirm,
            "the nested theme neither inherited nor reset to the defaults, which " +
                "is a third behaviour nothing describes",
        )
    }

    @Test
    fun theHandRolledVersionIsTheDefectThisFunctionRemoves() {
        val hand = readInside { inner ->
            CompositionLocalProvider(LocalColourScheme provides Other, content = inner)
        }
        assertNotEquals(
            Other.content, hand.contentColour,
            "CompositionLocalProvider(LocalColourScheme provides …) now carries the " +
                "content colour with it, so ProvideTokens' central argument is stale",
        )

        val provided = readInside { inner -> ProvideTokens(colours = Other, content = inner) }
        assertEquals(
            Other.content, provided.contentColour,
            "ProvideTokens left LocalContentColour holding the outgoing scheme's " +
                "colour (${hand.contentColour}), so every Text and Icon below it " +
                "draws in a palette that is no longer in force",
        )
        assertEquals(
            Other.content, provided.textStyle.color.takeOrElse { provided.contentColour },
            "the text style did not follow the typography either",
        )
    }

    /**
     * A `Surface`'s narrowing survives, because the ground it painted has not
     * been repainted.
     *
     * The re-provision above is right for a subtree hanging off the page and
     * wrong inside a coloured container: that container already drew itself in
     * the *outgoing* palette, so the colour that reads on it is still the
     * outgoing `onPrimary`. Replacing it with the incoming scheme's page content
     * colour would put page-coloured text on a primary-coloured box.
     */
    @Test
    fun aSurfacesOwnContentColourIsNotOverwritten() {
        val seen = readInside { inner ->
            Surface(colour = Kontour.primary, contentColour = Kontour.onPrimary) {
                ProvideTokens(colours = Other, content = inner)
            }
        }
        assertEquals(
            Kontour.onPrimary, seen.contentColour,
            "ProvideTokens overwrote a Surface's content colour with the incoming " +
                "scheme's page colour. The Surface behind it is still painted in " +
                "the outgoing palette, so its narrowing was the correct value and " +
                "this is now page-coloured text on a primary-coloured box.",
        )
    }

    /** What a composable one level down can see. */
    private class Seen(
        val colours: ColourScheme,
        val strings: Strings,
        val spacing: Spacing,
        val contentColour: Color,
        val textStyle: androidx.compose.ui.text.TextStyle,
    )

    /**
     * Reads the tokens in force inside [wrapper], which is placed inside a
     * [KontourTheme] carrying German strings and a tightened spacing scale.
     *
     * Two non-default token families rather than one, because a wrapper that
     * dropped exactly the family under test and kept the rest would pass a
     * single-family check while being broken.
     */
    private fun readInside(wrapper: @Composable (@Composable () -> Unit) -> Unit): Seen {
        val out = mutableStateOf<Seen?>(null)
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, strings = German, spacing = Tight) {
                    Box {
                        wrapper {
                            out.value = Seen(
                                colours = Theme.colours,
                                strings = Theme.strings,
                                spacing = Theme.spacing,
                                contentColour = LocalContentColour.current,
                                textStyle = LocalTextStyle.current,
                            )
                        }
                    }
                }
            }
            waitForIdle()
        }
        return requireNotNull(out.value) { "nothing was composed, so this measures nothing" }
    }

    private companion object {
        val German = Strings(confirm = "Bestätigen")
        val Tight = Spacing(md = androidx.compose.ui.unit.Dp(11f))
        val Kontour = kontourColourScheme(dark = false, contrast = ContrastLevel.Standard)
        val Other = kontourColourScheme(dark = true, contrast = ContrastLevel.Standard)
    }
}

private fun Color.takeOrElse(other: () -> Color): Color =
    if (this == Color.Unspecified) other() else this
