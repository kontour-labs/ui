package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every control in the gallery does something when you press it.
 *
 * The catalog is a place to *try* the components, and a specimen wired to
 * `onClick = {}` looks alive while doing nothing — it still presses, still
 * ripples, still focuses. This project has twice found a real defect hiding
 * behind exactly that: a callback nobody could tell was not being called.
 *
 * So this presses everything, and requires that **something** changed — either
 * the semantics tree, or a message reaching [LocalCatalogEcho], which is where a
 * specimen with no state of its own sends its press.
 *
 * The press is the node's own `OnClick` **semantics action**, not a synthesised
 * pointer. A synthesised one carries a pointer with it, and the pointer is its
 * own answer: it focuses what it lands on, it scales it while held, and it
 * enters and leaves a hover — which is enough to reopen and re-close a submenu
 * inside the window being measured. Invoking the action asks the only question
 * worth asking, which is whether the callback behind it does anything.
 *
 * ### What is allowed not to respond
 *
 * Whatever is **disabled**, and nothing else — there is no exemption list. A
 * disabled specimen is on show precisely to swallow the press, and a button with
 * a request already in flight is disabled while it flies. If something turns up
 * in the failure message that genuinely should not respond, disable it: a
 * control that looks pressable and is not is the bug this exists to catch.
 *
 * `ThemeShowcase` is absent because it has no controls at all — it is a page of
 * colour and type samples, and there is nothing on it to press.
 *
 * ### One composition, and a second only for a suspect
 *
 * Pressing something can open a sheet, close a menu or swap a page, so the tree
 * one press leaves behind is not the tree the next control was found in. Nodes
 * are therefore tracked by **semantics id** rather than by index, and one that
 * has vanished counts as having responded — disappearing is a response.
 *
 * A press can also *block* or *remove* the ones after it — a sheet opens over
 * them, a menu closes and takes its items with it — and both look exactly like a
 * dead control. So anything that reports dead, and anything that went missing
 * before its turn, is retried alone on a fresh page, and only a second silence
 * counts. Re-composing for every control instead — the obvious version — costs
 * minutes: the navigation page alone has 65.
 *
 * ### What it cannot see
 *
 * A control whose *container* answers the press regardless. A menu row closes
 * the menu whether or not its own callback does anything, and this cannot tell
 * the two apart, because the menu genuinely did respond. Reverting a menu item's
 * wiring leaves this test green — the one hole known in it.
 */
@OptIn(ExperimentalTestApi::class)
class EverythingRespondsTest {

    /**
     * What is expected to respond: everything enabled.
     *
     * A disabled control keeps its click action in the semantics tree and adds
     * `Disabled` beside it, so filtering on the action alone would ask the
     * disabled specimens to do something — which is the one thing they are on
     * show *not* to do.
     */
    private val pressable = hasClickAction() and
        isEnabled() and
        // Not a text field. Clicking one focuses it and nothing else, and focus
        // is the one signal this cannot read — a dead button focuses too. A
        // field's interactivity is typing, which `TextShowcase`'s own tests
        // cover; there is no callback here for a catalog to leave dangling.
        SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText) and
        // Not something already chosen. Pressing the selected radio, tab or
        // segment is *defined* to do nothing — a radio cannot be unselected by
        // pressing it — so silence there is the component working.
        SemanticsMatcher("is not already selected") {
            it.config.getOrNull(SemanticsProperties.Selected) != true
        }

    private val everything = SemanticsMatcher("any node") { true }

    /**
     * Big enough for the largest showcase to lay out inside it.
     *
     * The default test surface is 1024×768, and a node laid out beyond it
     * collapses to zero bounds — which a click lands nowhere in, silently. That
     * is not a small gap: at the default size, 57 of the navigation page's 65
     * controls were unreachable, and every one of them would have been reported
     * as dead.
     *
     * Paid for with [CanvasDensity] rather than with pixels. The widest page is
     * about 2100dp across and 2200 down; covering that at the goldens' 2× is a
     * 19-megapixel surface, and nothing here reads a pixel — every assertion is
     * on semantics.
     */
    private val Canvas = 1200
    private val CanvasDensity = 0.5f

    @Test fun actionsRespond() = check("actions") { ButtonShowcase() }

    @Test fun selectionControlsRespond() = check("selection") { SelectionShowcase() }

    @Test fun textFieldsRespond() = check("text") { TextShowcase() }

    @Test fun dateAndTimeRespond() = check("datetime") { DateTimeShowcase() }

    @Test fun overlaysRespond() = check("overlays") { OverlayShowcase() }

    @Test fun formsRespond() = check("forms") { SelectShowcase() }

    @Test fun sheetsRespond() = check("sheets") { SheetShowcase() }

    @Test fun listsRespond() = check("lists") { ListShowcase() }

    @Test fun navigationResponds() = check("nav") { NavShowcase() }

    @Test fun adaptiveLayoutsRespond() = check("adaptive") { AdaptiveShowcase() }

    @Test fun displayComponentsRespond() = check("display") { DisplayShowcase() }

    /**
     * Presses everything on one page.
     *
     * One test per page rather than one for the gallery: a failure names the page
     * it is on, and each page gets its own timeout instead of eleven sharing one.
     */
    private fun check(showcase: String, content: @Composable () -> Unit) {
        val suspect = mutableListOf<Pair<Int, String>>()
        var count = 0

        runDesktopComposeUiTest(width = Canvas, height = Canvas) {
            val echoed = mutableListOf<String>()
            // Hand-driven, because something on the overlays page never stops
            // asking for frames and an auto-advancing clock chases it forever.
            // Nothing here is waiting on an animation: a press either changes
            // state, which recomposes, or it does not.
            mainClock.autoAdvance = false
            setContent { Harness(echo = { echoed += it }, content = content) }
            settle()

            val initial = onAllNodes(pressable).fetchSemanticsNodes()
            val ids = initial.map { it.id }
            val labels = initial.map { it.describe() }
            count = ids.size

            ids.forEachIndexed { index, id ->
                val present = onAllNodes(pressable).fetchSemanticsNodes().any { it.id == id }
                if (!present) {
                    // Gone, because something pressed earlier removed it. Sent
                    // to the retry rather than waved through: counting it as
                    // responded is how a dead button inside a sheet that an
                    // earlier press closed slips past unpressed.
                    suspect += index to labels[index]
                    return@forEachIndexed
                }

                val before = fingerprint()
                val echoes = echoed.size

                onNode(withId(id)).performSemanticsAction(SemanticsActions.OnClick)

                if (!respondedTo(before) && echoed.size == echoes) {
                    suspect += index to labels[index]
                }
            }
        }

        val dead = suspect.filter { (index, _) -> isDeadAlone(content, index) }

        assertTrue(
            count > 0,
            "$showcase reported no click actions at all, so this checked nothing",
        )
        assertTrue(
            dead.isEmpty(),
            "${dead.size} of $count pressable things in $showcase do nothing " +
                "when pressed — a specimen wired to a callback that goes " +
                "nowhere looks alive and is not:\n" +
                dead.joinToString("\n") { (_, described) -> "  · $described" },
        )
    }

    /**
     * Whether pressing just this one thing, on a fresh page, changes nothing.
     *
     * Addressed by its **position** among the page's pressable nodes, not by its
     * semantics id. Ids come from a counter that keeps running for the life of
     * the process, so the same control gets a different one in every
     * composition — and looking it up by id in a fresh page found nothing, every
     * time, which silently cleared every suspect and made this whole test unable
     * to fail. Found by reverting three wirings and watching it stay green.
     */
    private fun isDeadAlone(content: @Composable () -> Unit, index: Int): Boolean {
        var dead = false

        runDesktopComposeUiTest(width = Canvas, height = Canvas) {
            val echoed = mutableListOf<String>()
            mainClock.autoAdvance = false
            setContent { Harness(echo = { echoed += it }, content = content) }
            settle()

            if (index >= onAllNodes(pressable).fetchSemanticsNodes().size) {
                return@runDesktopComposeUiTest
            }

            val before = fingerprint()
            onAllNodes(pressable)[index].performSemanticsAction(SemanticsActions.OnClick)

            dead = !respondedTo(before) && echoed.isEmpty()
        }

        return dead
    }

    /**
     * Whether the page looks different from [before] once the press has landed.
     *
     * **One reading, taken late.** Sampling at several moments instead was
     * tried and is worse than it sounds: the page is never quite still — a
     * spinner turns, a sheet settles — so *some* sample always differs and every
     * press passes. Reverting five wirings caught five with one late reading and
     * two with three readings, which is the whole argument.
     *
     * Late enough for a sheet to finish sliding away, since that is the slowest
     * thing a press does here. The clock is hand-driven: waiting for idle would
     * never return, because the overlays page has an indeterminate spinner and a
     * coach-mark queue and neither ever finishes.
     */
    private fun ComposeUiTest.respondedTo(before: Int): Boolean {
        settle()
        return fingerprint() != before
    }

    /** Half a second of frames — long enough for anything here to arrive. */
    private fun ComposeUiTest.settle() {
        repeat(30) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    private fun withId(id: Int) = SemanticsMatcher("id == $id") { it.id == id }

    /** Enough to find the thing in the source, without printing its whole config. */
    private fun SemanticsNode.describe(): String {
        val described = config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(", ")
        val text = config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(", ") { it.text }
        val role = config.getOrNull(SemanticsProperties.Role)?.toString()
        val what = listOfNotNull(described, text, role)
            .joinToString(" · ")
            .ifEmpty { "unlabelled" }
        return "$what — at ${boundsInRoot.left.toInt()}, ${boundsInRoot.top.toInt()}"
    }

    /**
     * The state of the page, as a number.
     *
     * ### What is in it, and why it is a list rather than the whole config
     *
     * The first version of this hashed `config.toString()`, and it was
     * **vacuous**: an accessibility action prints as
     * `AccessibilityAction(label=…, action=…Lambda$123@6f2b958e)`, and every
     * recomposition makes new lambdas — so any press at all changed the number
     * and every control in the gallery "responded". Reverting three wirings and
     * watching the test stay green is how that was found.
     *
     * `Focused` is left out for the same reason from the other end: pressing a
     * control focuses it whatever its callback does, so counting focus would
     * pass a dead button on the strength of the press mechanism alone.
     *
     * Bounds **are** in it, and that took two goes. A press scales the thing
     * pressed and `boundsInRoot` carries the transform, so while the shrink was
     * still springing back every press had moved something whatever its callback
     * did — which is why [settle] waits half a second rather than three frames.
     * They have to be in it: a non-modal sheet closing does not leave the
     * composition, it slides off, and its bounds are the only thing that says so.
     *
     * What is left is state a user could point at — the words on it, what it
     * says it is, whether it is on, where it is — plus the set of nodes present,
     * so something appearing or disappearing counts too.
     */
    private val watched = listOf(
        SemanticsProperties.Text,
        SemanticsProperties.EditableText,
        SemanticsProperties.ContentDescription,
        SemanticsProperties.StateDescription,
        SemanticsProperties.Selected,
        SemanticsProperties.ToggleableState,
        SemanticsProperties.ProgressBarRangeInfo,
        SemanticsProperties.TextSelectionRange,
        SemanticsProperties.Role,
        SemanticsProperties.Disabled,
    )

    private fun ComposeUiTest.fingerprint(): Int =
        onAllNodes(everything, useUnmergedTree = true).fetchSemanticsNodes()
            .fold(7) { hash, node -> hash * 31 + node.mark() }

    private fun SemanticsNode.mark(): Int {
        var hash = id
        hash = hash * 31 + boundsInRoot.hashCode()
        hash = hash * 31 + size.hashCode()
        for (key in watched) {
            hash = hash * 31 + config.getOrNull(key).toString().hashCode()
        }
        return hash
    }

    /**
     * A showcase as the gallery renders it: a theme, a root overlay host, and
     * somewhere for a press with no state of its own to land.
     *
     * `reduceMotion` so a press's only observable effect cannot be an animation
     * still settling when the tree is read back.
     */
    @Composable
    private fun Harness(echo: (String) -> Unit, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(CanvasDensity, fontScale = 1f),
            LocalCatalogEcho provides echo,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
