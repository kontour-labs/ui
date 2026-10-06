package io.kontour.ui.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which files a field or a target takes, from the patterns a caller writes.
 *
 * Checked by name and by type both, since no platform reports both for every
 * file: a desktop's dialog knows the name and guesses the type, a browser's
 * input knows the type the system told it, and a dropped file can have either.
 */
class FileTypesTest {

    @Test
    fun anyTakesEverything() {
        assertTrue(FileTypes.Any.accepts("notes", null))
        assertTrue(FileTypes.Any.accepts("timetable.pdf", "application/pdf"))
    }

    @Test
    fun anExtensionMatchesTheNameWhateverTheCase() {
        val pdf = FileTypes.of(".pdf")
        assertTrue(pdf.accepts("Timetable.PDF", null))
        assertFalse(pdf.accepts("timetable.pdf.zip", null))
        // Without its dot, it is still an extension.
        assertTrue(FileTypes.of("csv").accepts("fares.csv", "text/csv"))
    }

    @Test
    fun aTypeMatchesTheReportedTypeOrOneGuessedFromTheName() {
        val png = FileTypes.of("image/png")
        assertTrue(png.accepts("map", "image/png"))
        assertTrue(png.accepts("map.png", null), "a type nobody reported is taken from the extension")
        assertFalse(png.accepts("map.jpg", "image/jpeg"))
        assertFalse(png.accepts("map", null), "a file with neither cannot be shown to be one")
    }

    @Test
    fun aWildcardTakesTheWholeFamily() {
        val images = FileTypes.of("image/*")
        assertTrue(images.accepts("a.heic", null))
        assertTrue(images.accepts("b", "image/webp"))
        assertFalse(images.accepts("c.pdf", null))
        // "image/" is the family, not a prefix of any word: "imagery/x" is not one.
        assertFalse(images.accepts("d", "imagery/x"))
    }

    @Test
    fun anyPatternIsEnough() {
        val either = FileTypes.of("image/*", ".pdf")
        assertTrue(either.accepts("a.pdf", null))
        assertTrue(either.accepts("b.png", null))
        assertFalse(either.accepts("c.txt", null))
    }

    @Test
    fun patternsSplitIntoTypesAndExtensionsForThePickers() {
        val types = FileTypes.of(" Image/* ", ".PDF", "csv", "")
        assertEquals(listOf("image/*"), types.mimeTypes)
        assertEquals(listOf("pdf", "csv"), types.extensions)
        assertEquals(types, FileTypes.of("image/*", ".pdf", ".csv"))
    }
}
