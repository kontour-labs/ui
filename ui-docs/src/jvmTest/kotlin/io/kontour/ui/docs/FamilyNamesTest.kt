package io.kontour.ui.docs

import io.kontour.ui.demo.DemoFamily
import io.kontour.ui.demo.demoFamilies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The gallery's families and the documentation's are the same families.
 *
 * Two lists name them: `demoFamilies` in `:ui-catalog`, which is now the *only*
 * list of what the gallery shows, and [familyOrder] here, which is the order the
 * documentation index leads a reader through. Neither module can check this
 * alone — the dependency runs `:ui-docs` → `:ui-catalog`, so `:ui-catalog`
 * cannot see [familyOrder], and this is the one place both are visible.
 *
 * ### Why a name, and not an id
 *
 * The obvious hardening is to make the family an enum shared by both modules,
 * and it is the wrong trade twice over. `MAX_UNSWEPT_ENUMS` is at its cap of 9,
 * so a tenth public enum costs a knob sweep somewhere else. And the docs' list
 * carries two families the gallery has no demos for — `Guides` and `Other` are
 * prose, not components — so the shared type would have members half of it had
 * to special-case. A string plus this test is the cheaper half of that trade,
 * and it fails at the same moment the enum would.
 */
class FamilyNamesTest {

    @Test
    fun everyDemoFamilyIsAFamilyTheDocsKnow() {
        val unknown = demoFamilies.map(DemoFamily::name).filterNot { it in familyOrder }
        if (unknown.isNotEmpty()) {
            fail(
                "familyOrder has no section for ${unknown.size} of the " +
                    "${demoFamilies.size} demo families, so the gallery shows a " +
                    "page the documentation index does not list: " +
                    unknown.joinToString(", ") { "\"$it\"" } +
                    ". Known families are: " + familyOrder.joinToString(", ") { "\"$it\"" },
            )
        }
    }

    /**
     * And in the same order.
     *
     * A reader who meets Actions, then Selection, then Text editing in the index
     * and then Text editing, then Actions, then Selection in the gallery has met
     * two orderings of one thing. Both lists are hand-written and deliberate —
     * `familyOrder`'s KDoc argues at length that alphabetical would be a list
     * sorted by a fact about spelling — so the check is that the deliberate
     * orders agree, not that either is derived from the other.
     */
    @Test
    fun theTwoListsAgreeOnTheOrder() {
        val demoNames = demoFamilies.map(DemoFamily::name)
        assertEquals(
            familyOrder.filter { it in demoNames },
            demoNames,
            "the gallery shows its families in a different order from the " +
                "documentation index. Both orders are chosen rather than sorted, " +
                "so one of them has been edited without the other.",
        )
    }

    /**
     * The docs' extra families are prose, and are expected.
     *
     * The subset above is one-directional on purpose, which would let
     * `familyOrder` grow a component family the gallery never showed. Naming the
     * two that legitimately have no demos turns that silence into a decision:
     * a third one appearing here is either a documentation family missing its
     * demos or a name that wants adding to this list, and either way somebody
     * looks.
     */
    @Test
    fun onlyTheProseFamiliesHaveNoDemos() {
        val demoNames = demoFamilies.map(DemoFamily::name).toSet()
        assertEquals(
            listOf("Guides", "Other"),
            familyOrder.filterNot { it in demoNames },
            "a documentation family other than the two prose ones has no demos " +
                "in the gallery",
        )
    }
}
