package io.kontour.ui.components.text

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A file's size, as a file field writes it: counted in thousands, as the
 * platforms' own file browsers count, and never to more places than it needs.
 */
@OptIn(ExperimentalTestApi::class)
class FileSizeLabelTest {

    @Test
    fun underAThousandIsBytes() {
        assertEquals(SizeReading(0.0, SizeUnit.Bytes, 0), readSize(0))
        assertEquals(SizeReading(999.0, SizeUnit.Bytes, 0), readSize(999))
        assertEquals(SizeReading(0.0, SizeUnit.Bytes, 0), readSize(-5), "a size a platform got wrong is not negative")
    }

    @Test
    fun onePlaceUnderTenAndNoneAbove() {
        assertEquals(SizeReading(2.4, SizeUnit.Megabytes, 1), readSize(2_400_000))
        assertEquals(SizeReading(24.0, SizeUnit.Megabytes, 0), readSize(24_000_000))
        assertEquals(SizeReading(1.5, SizeUnit.Kilobytes, 1), readSize(1_500))
    }

    @Test
    fun aNoughtInThePlaceIsLeftOff() {
        assertEquals(SizeReading(2.0, SizeUnit.Megabytes, 0), readSize(2_000_000))
        assertEquals(SizeReading(10.0, SizeUnit.Kilobytes, 0), readSize(9_960), "9.96 rounds to ten, and ten has no place")
    }

    @Test
    fun aThousandOfOneIsOneOfTheNext() {
        assertEquals(SizeReading(1.0, SizeUnit.Megabytes, 0), readSize(999_600))
        assertEquals(SizeReading(1.0, SizeUnit.Gigabytes, 0), readSize(999_999_999))
        // There is no unit after a gigabyte here, so a big one stays in them.
        assertEquals(SizeReading(1500.0, SizeUnit.Gigabytes, 0), readSize(1_500_000_000_000))
    }

    @Test
    fun theLabelIsInTheReadersWords() = runComposeUiTest {
        var label: (Long) -> String = { "" }
        setContent { KontourTheme { label = FileFieldDefaults.sizeLabel() } }
        assertEquals("1 byte", label(1))
        assertEquals("512 bytes", label(512))
        assertEquals("2.4 MB", label(2_400_000))
        assertEquals("24 MB", label(24_000_000))
    }
}
