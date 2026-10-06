package io.kontour.ui.dragdrop

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A desktop drag from outside the app — a file manager's files, another
 * window's text — read as AWT hands it over, from a transferable built here
 * rather than a real drag, which a test cannot make.
 */
class AwtDropParsingTest {

    private class FileListTransferable(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = files
    }

    @Test
    fun filesFromTheFileManagerArriveAsPickedFiles() = runBlocking {
        val path = Files.createTempFile("timetable", ".csv")
        path.writeText("route,stop\n960,Curtin\n")
        val transferable = FileListTransferable(listOf(path.toFile()))

        val info = assertNotNull(AwtDropReader.infoOf(transferable))
        assertTrue(info.hasFiles, "a file list is files")

        val dropped = assertNotNull(AwtDropReader.readFrom(transferable))
        assertEquals(listOf(path.fileName.toString()), dropped.files.map { it.name })
        assertEquals("route,stop\n960,Curtin\n", dropped.files.single().readText())
        assertTrue(!dropped.hasText)
        Files.delete(path)
    }

    @Test
    fun textFromAnotherWindowArrivesAsText() = runBlocking {
        val transferable = StringSelection("Platform 2, Perth Underground")
        val info = assertNotNull(AwtDropReader.infoOf(transferable))
        assertTrue(info.hasText && !info.hasFiles)
        val dropped = assertNotNull(AwtDropReader.readFrom(transferable))
        assertTrue(dropped.files.isEmpty())
        assertEquals("Platform 2, Perth Underground", dropped.readText())
    }

    @Test
    fun aDragOfNeitherIsNotOffered() {
        val empty = object : Transferable {
            override fun getTransferDataFlavors() = emptyArray<DataFlavor>()
            override fun isDataFlavorSupported(flavor: DataFlavor) = false
            override fun getTransferData(flavor: DataFlavor): Any = error("nothing")
        }
        assertNull(AwtDropReader.infoOf(empty))
    }
}
