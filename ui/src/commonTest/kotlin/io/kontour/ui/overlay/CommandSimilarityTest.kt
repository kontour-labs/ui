package io.kontour.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tiers [commandSimilarity] ranks by, and that it agrees with [commandMatches] on what answers at all. */
class CommandSimilarityTest {

    private fun command(label: String, vararg keywords: String) = Command(label, label, onAction = {}, keywords = keywords.toList())

    private fun closer(query: String, first: Command, second: Command) {
        val a = commandSimilarity(first, query)
        val b = commandSimilarity(second, query)
        assertTrue(a > b, "for \"$query\", ${first.label} (${first.keywords}) scored $a, not above ${second.label} (${second.keywords}) at $b")
    }

    @Test
    fun theTiersGoFromTheWholeLabelToInsideAKeyword() {
        closer("save", command("Save"), command("Save as…"))
        closer("save", command("Save as…"), command("Autosave", "save"))
        closer("sav", command("Save all"), command("Quick save"))
        closer("save", command("Quick save"), command("Disk", "save"))
        closer("save", command("Disk", "save"), command("Disk", "save as"))
        closer("save", command("Disk", "save as"), command("Autosave"))
        closer("save", command("Autosave"), command("Disk", "autosave"))
    }

    @Test
    fun moreOfTheLabelCoveredIsCloser() {
        closer("sav", command("Save"), command("Save as…"))
        closer("ave", command("Wave"), command("Autosave"))
    }

    @Test
    fun caseAndAccentsAreFoldedAsMatchingFoldsThem() {
        assertEquals(commandSimilarity(command("Café"), "cafe"), commandSimilarity(command("cafe"), "CAFE"))
    }

    @Test
    fun whatDoesNotMatchScoresNothing() {
        assertEquals(0f, commandSimilarity(command("Settings"), "zoom"))
        assertEquals(0f, commandSimilarity(command("Settings"), "  "))
        for (label in listOf("Save", "Autosave", "Disk")) {
            val c = command(label, "save as")
            assertEquals(commandMatches(c, "save"), commandSimilarity(c, "save") > 0f, label)
        }
    }
}
