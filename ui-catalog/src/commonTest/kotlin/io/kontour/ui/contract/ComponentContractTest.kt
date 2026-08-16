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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
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

    @Test
    fun theTouchTargetMeetsThePlatformMinimum() {
        forEachComponent { spec ->
            if (!spec.expectsMinimumTarget) return@forEachComponent
            runComposeUiTest {
                var minimum = 0f
                setContent {
                    Harness {
                        minimum = with(LocalDensity.current) {
                            Theme.sizing.minTouchTarget.toPx()
                        }
                        spec.content(Modifier.testTag(tag), true) {}
                    }
                }

                val size = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
                // Half a pixel of slack: the target is rounded to whole pixels,
                // and a 47.999 failure would be noise rather than a finding.
                assertTrue(
                    size.height >= minimum - 0.5f,
                    "${spec.name} is ${size.height}px tall, under the " +
                        "${minimum}px minimum — the visible control may be " +
                        "smaller, the target may not",
                )
                assertTrue(
                    size.width >= minimum - 0.5f,
                    "${spec.name} is ${size.width}px wide, under the ${minimum}px minimum",
                )
            }
        }
    }

    @Test
    fun everyComponentSurvivesLargeTextAndRtl() {
        forEachComponent { spec ->
            runComposeUiTest {
                setContent {
                    // 200% type in a narrow, RTL window — the combination that
                    // breaks a fixed-height row or a hard-coded start padding.
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 2f, fontScale = 2f),
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        Harness {
                            Box(Modifier.width(320.dp)) {
                                spec.content(Modifier.testTag(tag), true) {}
                            }
                        }
                    }
                }

                val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue(
                    bounds.width > 0f && bounds.height > 0f,
                    "${spec.name} collapsed at 200% type in RTL: ${bounds.width}×${bounds.height}",
                )
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
            ?.let { onNode(hasAnyAncestor(hasTestTag(tag)) and it) }
            ?: onNodeWithTag(tag)

    /**
     * Runs [check] for every registered component, reporting *all* the failures
     * rather than the first.
     *
     * A run that stops at the first failure turns a systematic mistake — one the
     * whole registry shares — into a dozen sequential fixes.
     */
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
