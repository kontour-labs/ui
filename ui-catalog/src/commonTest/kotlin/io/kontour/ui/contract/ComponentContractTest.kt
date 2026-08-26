package io.kontour.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The rules every interactive component keeps, asserted over all of them.
 *
 * The point is that the rules in `contributing.md` are *enforced* rather than
 * remembered. Each is a mistake that is easy to make, invisible in a screenshot,
 * and discovered only by the person it excludes:
 *
 * 1. **The modifier reaches the outermost node.** A component that drops it, or
 *    applies it to something inside itself, cannot be positioned by its caller —
 *    and the caller's `padding` silently lands in the wrong place.
 * 2. **Disabled means disabled.** Both halves: the callback does not fire, *and*
 *    assistive tech is told. A control that looks greyed out and still fires is a
 *    bug; one that blocks the callback without saying so is a control a screen
 *    reader user keeps trying.
 * 3. **A role is declared.** "Button" is what makes a control findable and
 *    operable; a node with no role is announced as text.
 * 4. **A visible label names the control.** Compose has no `labelledBy`, so a
 *    label drawn above a field stays an unrelated node however close it is.
 * 5. **The touch target meets the platform minimum**, even when the visible
 *    control is smaller.
 * 6. **It survives 200% font scale and RTL** without collapsing.
 *
 * A component missing from [componentRegistry] is a component none of this
 * applies to, which is why adding one there is part of adding a component.
 *
 * ### What this has actually caught
 *
 * Written after phases 0–12, it failed on its first run and found six real bugs
 * that had survived every screenshot review: `ListItem` and `SettingRow` dropped
 * their `clickable` when disabled and so announced as plain text with no role;
 * `IconToggleButton` wrapped a button in a switch; a disabled `Slider` still
 * exposed `setProgress`, so assistive tech could move a control that looked
 * inert; and no text field or select carried its label as a name. That is the
 * argument for a registry over per-component tests — none of these were in a
 * component anyone thought needed checking.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentContractTest {

    private val tag = "subject"

    @Test
    fun theModifierReachesTheOutermostNode() {
        forEachComponent { spec ->
            runComposeUiTest {
                setContent {
                    Harness { spec.content(Modifier.testTag(tag), true) {} }
                }
                // If the modifier were dropped, or applied to an inner node, the
                // tag would be missing or on something that is not the root.
                onNodeWithTag(tag).assertExists(
                    "${spec.name} did not apply its `modifier` — a caller cannot " +
                        "position or size it"
                )
            }
        }
    }

    @Test
    fun disabledBlocksTheCallbackAndSaysSo() {
        forEachComponent { spec ->
            runComposeUiTest {
                var fired = false
                setContent {
                    Harness { spec.content(Modifier.testTag(tag), false) { fired = true } }
                }

                if (spec.activatedByClick) {
                    control(spec).performClick()
                    assertTrue(
                        !fired,
                        "${spec.name} fired its callback while disabled",
                    )
                }

                // The other half. A control that blocks the callback without
                // reporting it is one a screen-reader user keeps trying.
                control(spec).assertIsNotEnabled()
            }
        }
    }

    @Test
    fun everyComponentDeclaresItsRole() {
        forEachComponent { spec ->
            val expected = spec.role ?: return@forEachComponent
            runComposeUiTest {
                setContent {
                    Harness { spec.content(Modifier.testTag(tag), true) {} }
                }

                val role = control(spec).fetchSemanticsNode()
                    .config.getOrNull(SemanticsProperties.Role)
                assertEquals(
                    expected,
                    role,
                    "${spec.name} announces itself as $role, not $expected",
                )
            }
        }
    }

    @Test
    fun everyLabelledControlAnnouncesItsLabel() {
        forEachComponent { spec ->
            val expected = spec.accessibleName ?: return@forEachComponent
            runComposeUiTest {
                setContent {
                    Harness { spec.content(Modifier.testTag(tag), true) {} }
                }

                // Either half counts: a control can be named by a description or
                // by text merged into it. What does *not* count is a label drawn
                // next to it — that is the failure this catches, and it is
                // invisible in every screenshot because it looks correct.
                val config = control(spec).fetchSemanticsNode().config
                val spoken = buildList {
                    config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
                    config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                }
                assertTrue(
                    spoken.any { it.contains(expected) },
                    "${spec.name} is labelled \"$expected\" on screen but announces " +
                        "$spoken — the label is a sibling node, not the control's name",
                )
            }
        }
    }

    @Test
    fun everyControlAnnouncesSomething() {
        forEachComponent { spec ->
            // Something with no role is not a control and has nothing to be
            // called; a `namedByContext` one is named by the row it sits in.
            // Everything else has to answer to a name.
            spec.role ?: return@forEachComponent
            if (spec.namedByContext) return@forEachComponent
            runComposeUiTest {
                setContent {
                    Harness { spec.content(Modifier.testTag(tag), true) {} }
                }

                val config = control(spec).fetchSemanticsNode().config
                val spoken = buildList {
                    config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
                    config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                    config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
                }
                assertTrue(
                    spoken.any { it.isNotBlank() },
                    "${spec.name} announces nothing at all — a screen reader reaches " +
                        "it and has no way to say what it is",
                )
            }
        }
    }

    /**
     * The touch target meets the minimum **on every platform**, not on this one.
     *
     * `platformMinTouchTarget` is 48dp on Android, 44 on iOS and web, and **24dp
     * on the JVM** — and every test in this repository runs on the JVM. So for as
     * long as this asserted against `Theme.sizing.minTouchTarget` it was asking
     * whether 29 components clear a bar that nothing in the library is under. It
     * could not fail. It never had.
     *
     * That is not a hypothetical. Four defects in three rounds lived on exactly
     * this axis and none of them were caught here: a `ButtonGroup` whose 1dp seam
     * rendered at 9dp on Android, a `SegmentedControl` 4dp shorter than the
     * buttons beside it, a `PasswordField` 20dp taller than the field above it,
     * and a `Switch` that took the frame down when squeezed. Each was found on a
     * phone, by hand.
     *
     * So the minimum is supplied rather than read. `KontourTheme` takes a
     * `sizing`, which is the same lever `PhoneWidthTest` uses to render an honest
     * phone.
     *
     * ### And it is measured from a wrapper, not from `touchBoundsInRoot`
     *
     * `touchBoundsInRoot` is not the component's reserved space. It is the
     * semantics node's own bounds inflated to Compose's
     * `ViewConfiguration.minimumTouchTargetSize` — a framework constant of 48dp
     * that has nothing to do with this library. A `Checkbox` whose wrapper
     * measures 96×96 still reports `touchBoundsInRoot` of 48×48, and a component
     * that reserved *nothing* would report 48 as well. So the old assertion
     * passed at 24dp and 44dp for free, passed at 48dp because 48 is the
     * constant, and could not have failed either way.
     *
     * `minimumTouchTarget` promises to *reserve layout space* — "a 20dp checkbox
     * stays a 20dp checkbox on screen; it just reserves 48dp of layout space and
     * centres itself in it". A box wrapped round the component measures exactly
     * that promise, which is why it is the thing measured.
     *
     * Ascending, so a component that fails at 24dp is reported as failing at
     * 24dp: "under even the pointer minimum" and "under Android's" are different
     * findings and the first is worse.
     *
     * ### The four opt-outs were surveyed rather than trusted
     *
     * `Stepper`, `TextField`, `SearchField` and `Select` carry
     * `expectsMinimumTarget = false`, each for the same stated reason: the
     * outermost node is a container and the controls are inside it. Measured
     * anyway at 48dp they come out 144×48, 1024×76, 1024×52 and 1024×76 — all
     * comfortably over. Turning the rule back on for them would therefore add
     * four assertions that cannot fail and, worse, would look like coverage of
     * the buttons inside a `Stepper` while measuring the row around them. The
     * opt-outs stay, and the per-component tests keep the inside honest.
     */
    @Test
    fun theTouchTargetMeetsEveryPlatformMinimum() {
        forEachComponent { spec ->
            if (!spec.expectsMinimumTarget) return@forEachComponent
            for ((platform, target) in Platforms) {
                runComposeUiTest {
                    var minimum = 0f
                    setContent {
                        Harness(target) {
                            minimum = with(LocalDensity.current) { target.toPx() }
                            // Wrapping, so what is measured is the space the
                            // component reserved rather than the space it drew in.
                            Box(Modifier.testTag(reserved)) {
                                spec.content(Modifier.testTag(tag), true) {}
                            }
                        }
                    }

                    val size = onNodeWithTag(reserved).fetchSemanticsNode().size
                    // Half a pixel of slack: the target is rounded to whole
                    // pixels, and a 47.999 failure would be noise rather than a
                    // finding.
                    assertTrue(
                        size.height >= minimum - 0.5f,
                        "${spec.name} reserves ${size.height}px of height against " +
                            "$platform's $target ($minimum px) — the visible " +
                            "control may be smaller, the target may not",
                    )
                    assertTrue(
                        size.width >= minimum - 0.5f,
                        "${spec.name} reserves ${size.width}px of width against " +
                            "$platform's $target ($minimum px)",
                    )
                }
            }
        }
    }

    /**
     * 200% type, RTL, a narrow window **and a phone's touch target**.
     *
     * The last of those is new and is the one that matters. 200% type on its own
     * is a font problem; 200% type *next to a 48dp target* is what made a
     * `PasswordField` stand 20dp taller than the field above it, because the
     * frame grows to whatever is tallest inside it and both were competing to
     * be that. The two axes are only interesting together.
     *
     * The assertion used to be `width > 0 && height > 0`, which catches a total
     * collapse and nothing else — it would have passed a component twice the
     * width of the window it was given. It now also checks the component stays
     * inside its container, which is the only overflow a layout assertion can
     * see: constraints stop a component measuring wider than its box, so
     * exceeding one means `requiredWidth`, an `offset`, or unbounded wrap
     * content. Ink spilling past the bounds needs pixels and belongs to
     * `WidthSweepTest`.
     */
    @Test
    fun everyComponentSurvivesLargeTextAndRtl() {
        forEachComponent { spec ->
            for ((platform, target) in Platforms) {
                runComposeUiTest {
                    var containerPx = 0f
                    setContent {
                        // 200% type in a narrow, RTL window — the combination
                        // that breaks a fixed-height row or a hard-coded start
                        // padding.
                        CompositionLocalProvider(
                            LocalDensity provides Density(density = 2f, fontScale = 2f),
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Harness(target) {
                                containerPx = with(LocalDensity.current) { NarrowWindow.toPx() }
                                Box(Modifier.width(NarrowWindow)) {
                                    spec.content(Modifier.testTag(tag), true) {}
                                }
                            }
                        }
                    }

                    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        bounds.width > 0f && bounds.height > 0f,
                        "${spec.name} collapsed at 200% type in RTL on $platform: " +
                            "${bounds.width}×${bounds.height}",
                    )
                    assertTrue(
                        bounds.width <= containerPx + 0.5f,
                        "${spec.name} is ${bounds.width}px wide in a ${containerPx}px " +
                            "window at 200% type on $platform — it is laying out " +
                            "past what it was given, so whatever is beside it is " +
                            "being pushed off or drawn over",
                    )
                }
            }
        }
    }

    /**
     * What every component is rendered inside: the theme, and the overlay host a
     * real app root provides. Anything that opens a menu, sheet or toast needs
     * the host to exist even when it is closed, so the harness matches the app
     * rather than the minimum a component happens to get away with.
     */
    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            OverlayHost { content() }
        }
    }

    /** The same, with a named platform's touch minimum in force. */
    @Composable
    private fun Harness(minTouchTarget: Dp, content: @Composable () -> Unit) {
        KontourTheme(
            reduceMotion = true,
            sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = minTouchTarget),
        ) {
            OverlayHost { content() }
        }
    }

    /**
     * The node the role and disabled rules apply to.
     *
     * The tagged node for most components; for a container round a control — an
     * `Accordion`, a text field's label-and-helper scaffold — the control inside
     * it, found by the matcher the spec supplies. Constrained to the subtree so a
     * component that renders two clickable things fails loudly here rather than
     * quietly asserting against the wrong one.
     */
    private fun ComposeUiTest.control(spec: ComponentSpec): SemanticsNodeInteraction =
        spec.control
            ?.let { onNode(hasAnyAncestor(hasTestTag(tag)) and it.matcher()) }
            ?: onNodeWithTag(tag)

    /**
     * The locator, as something the test framework can look for.
     *
     * The registry names a *kind* of node rather than holding a matcher, so the
     * list can live in `commonMain` and be read by the documentation site
     * without dragging `compose.uiTest` into a browser bundle. This is the one
     * place that translation happens.
     */
    private fun ControlLocator.matcher(): SemanticsMatcher = when (this) {
        ControlLocator.Clickable -> hasClickAction()
        ControlLocator.TextInput ->
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        ControlLocator.SelectedOption ->
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        is ControlLocator.ClickableLabelled -> hasClickAction() and hasText(text)
    }

    /**
     * Runs [check] for every registered component, reporting *all* the failures
     * rather than the first.
     *
     * A run that stops at the first failure turns a systematic mistake — one the
     * whole registry shares — into a dozen sequential fixes.
     */
    /**
     * Every touch minimum the library ships against, smallest first.
     *
     * These are `platformMinTouchTarget`'s three actual values. WCAG 2.2 SC 2.5.8
     * is the 24; Apple's HIG is the 44; Android's guidance is the 48.
     */
    /** The wrapper round a component, for measuring what it reserved. */
    private val reserved = "reserved"

    /** A small phone in portrait, less its margins. */
    private val NarrowWindow = 320.dp

    private val Platforms = listOf(
        "desktop" to 24.dp,
        "iOS and web" to 44.dp,
        "Android" to 48.dp,
    )

    private fun forEachComponent(check: (ComponentSpec) -> Unit) {
        // Only the ones under contract. The registry also carries specimens that
        // exist to be *drawn* — a `Kbd`, a `Scrollbar` — and every assertion
        // below asks a question about being operable, which those are not.
        val under = componentRegistry.filter { it.underContract }
        val failures = mutableListOf<String>()
        for (spec in under) {
            try {
                check(spec)
            } catch (error: Throwable) {
                failures += "${spec.name}: ${error.message?.lineSequence()?.first()}"
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of ${under.size} components failed:\n" +
                    failures.joinToString("\n") { "  · $it" }
            )
        }
    }
}
