package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/** An avatar says what its decorations mean, after the name, since a dot is drawn and not said. */
@OptIn(ExperimentalTestApi::class)
class AvatarDecorationTest {

    @Test
    fun theAnnouncementCarriesEveryDecoration() = runComposeUiTest {
        setContent {
            KontourTheme {
                Avatar(name = "Aaron") {
                    status(Color.Green, "Online")
                    count(3)
                    icon(SystemIcons.Close, description = null, at = AvatarCorner.BottomStart)
                }
            }
        }
        onNodeWithContentDescription("Aaron. Online. 3 new").assertExists()
    }

    @Test
    fun aCountPastItsCapIsSaidAsMoreThan() = runComposeUiTest {
        setContent {
            KontourTheme {
                Avatar(name = "Kit") { count(250, max = 99) }
            }
        }
        onNodeWithContentDescription("Kit. More than 99").assertExists()
    }
}
