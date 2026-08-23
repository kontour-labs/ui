package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.LocalSizing
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every single-line field is the same height, on every platform.
 *
 * They were not. `FieldScaffold` states its height as
 * `defaultMinSize(minHeight = controlHeightLarge)` — 52dp — and then padded the
 * frame 12dp top and bottom. `defaultMinSize` is a *minimum* and a `Row` grows
 * to its tallest child, so the padding applied to whatever a caller had put in a
 * slot; and a slot routinely holds an `IconButton`, which brings
 * `minimumTouchTarget` with it. 12 + 48 + 12 = **72dp**, against 52 for every
 * field without one.
 *
 * So on a phone a `PasswordField` with a reveal toggle stood 20dp taller than
 * the field above it, and a `SearchField` **grew by 20dp the moment the user
 * typed the first character** — because that is when its clear button appears.
 *
 * ### Why this needs `LocalSizing` overridden
 *
 * `platformMinTouchTarget` is 48dp on Android, 44 on iOS and web, and **24dp on
 * the JVM**. Every test here runs on the JVM, where nothing in a field slot is
 * smaller than 24dp — so `minimumTouchTarget` expands nothing, the arithmetic
 * comes out at 52dp in every case, and this whole class of defect is invisible
 * to an ordinary test and to every desktop golden. Overriding the sizing is what
 * makes the JVM answer the question a phone asks; a version of this test that
 * leaves it alone passes against the defect, which was checked rather than
 * assumed.
 *
 * The phone goldens are the exception, and worth being honest about: Round 11
 * taught `PhoneWidthTest` to render at Android's 48dp target for exactly this
 * reason, so `phone/text.png` has been showing a 72dp password field beside a
 * 52dp email field for as long as the fault existed. It was not unobservable
 * there. It was unread — which is the harder failure of the two, and the reason
 * this is a measurement with a number in its message rather than one more
 * picture to look at.
 */
@OptIn(ExperimentalTestApi::class)
class FieldHeightTest {

    @Test
    fun everySingleLineFieldIsTheSameHeight() {
        val heights = FieldsUnderTest.associateWith { field -> heightOf { field.content() } }

        val distinct = heights.values.distinct()
        assertTrue(
            distinct.size == 1,
            "single-line fields came out at ${distinct.sorted().joinToString("dp, ")}dp: " +
                heights.entries.joinToString { "${it.key.name} ${it.value}dp" } +
                ". They share one frame and one height token, and a form of them " +
                "is a column of boxes that has to line up.",
        )
        assertEquals(
            ExpectedHeight,
            distinct.single(),
            "the shared height is not `controlHeightLarge` any more",
        )
    }

    /**
     * Typing into a search field does not resize it.
     *
     * The clear button is revealed by the first character, so this is the same
     * defect seen from the side that a user actually notices: the field, and
     * everything laid out under it, jumped 20dp downward as they started to
     * type. `AnimatedVisibility` fades and scales the button but does not
     * animate the layout, so it was a jump rather than a slide.
     */
    @Test
    fun aSearchFieldDoesNotGrowWhenYouTypeInIt() {
        val empty = heightOf {
            SearchField(state = rememberTextFieldState(""), clearIcon = Tabler.Outline.X)
        }
        val typed = heightOf {
            SearchField(state = rememberTextFieldState("Perth"), clearIcon = Tabler.Outline.X)
        }

        assertEquals(
            empty,
            typed,
            "a search field is ${empty}dp empty and ${typed}dp with a word in it — " +
                "it changes size under the user as they type, and takes the page " +
                "below it with it",
        )
    }

    /** One field to render, named so a failure says which one. */
    private class Field(val name: String, val content: @Composable () -> Unit)

    private val FieldsUnderTest = listOf(
        Field("TextField") { TextField(state = rememberTextFieldState("Perth")) },
        Field("TextField+trailingIcon") {
            TextField(state = rememberTextFieldState("Perth"), trailingIcon = Tabler.Outline.Search)
        },
        Field("SearchField") {
            SearchField(state = rememberTextFieldState("Perth"), clearIcon = Tabler.Outline.X)
        },
        Field("PasswordField") {
            PasswordField(
                state = rememberTextFieldState("hunter2"),
                revealIcon = Tabler.Outline.Eye,
                hideIcon = Tabler.Outline.EyeOff,
            )
        },
        Field("EmailField") { EmailField(state = rememberTextFieldState("a@b.co")) },
        Field("NumberField") { NumberField(state = rememberTextFieldState("42")) },
        Field("PhoneField") { PhoneField(state = rememberTextFieldState("0400")) },
        Field("Select") {
            Select(
                value = "Tomorrow",
                onValueChange = {},
                options = listOf("Today", "Tomorrow"),
            )
        },
    )

    /**
     * The height of one field, in dp, with a phone's touch target in force.
     *
     * Measured from the tagged wrapper rather than from the field's own node,
     * because the frame is one row of a `Column` that also holds the label and
     * the helper slot, and those are not what is under test.
     */
    private fun heightOf(content: @Composable () -> Unit): Int {
        var height = -1

        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    CompositionLocalProvider(
                        LocalSizing provides Theme.sizing.copy(minTouchTarget = PhoneTarget)
                    ) {
                        // A `Select` renders its menu into an overlay host and
                        // fails loudly without one. It never opens here, but the
                        // host has to exist for the field to compose at all.
                        OverlayHost {
                            Box(Modifier.width(320.dp).testTag(Tag)) { content() }
                        }
                    }
                }
            }
            val density = androidx.compose.ui.unit.Density(1f)
            val px = onNodeWithTag(Tag).fetchSemanticsNode().size.height
            height = with(density) { px.toDp().value.toInt() }
        }

        return height
    }

    private companion object {
        const val Tag = "field"

        /** Android's minimum. iOS's 44dp reproduces the same fault one dp smaller. */
        val PhoneTarget = 48.dp

        /** `Theme.sizing.controlHeightLarge`, which is what a field's frame is. */
        const val ExpectedHeight = 52
    }
}
