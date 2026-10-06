package io.kontour.ui.files

import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/** The desktop's own picker, where it can be exercised without a screen. */
class DesktopFileChooserTest {

    /** A server, a CI runner: nothing to show a dialog on, and nothing was chosen. */
    @Test
    fun headlessThereIsNoDialogAndNothingIsChosen() {
        assumeTrue("needs a headless JVM", GraphicsEnvironment.isHeadless())
        var result: List<PickedFile>? = null
        DesktopFileChooser.choose(FileTypes.Any, multiple = false) { result = it }
        assertEquals(emptyList(), result, "answered at once, with nothing, rather than throwing HeadlessException")
    }

    @Test
    fun aFileOnDiskReadsAsItself() = runBlocking {
        val path = Files.createTempFile("fares", ".csv")
        path.writeText("zone,fare\n1,3.20\n")
        val file = DesktopFile(path.toFile())
        assertEquals(path.fileName.toString(), file.name)
        assertEquals(17L, file.size)
        assertTrue(FileTypes.of(".csv").accepts(file))
        assertEquals("zone,fare\n1,3.20\n", file.readText())
        Files.delete(path)
    }
}
