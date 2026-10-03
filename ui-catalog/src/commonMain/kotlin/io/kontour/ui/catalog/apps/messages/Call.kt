package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Microphone
import com.composables.icons.tabler.outline.PhoneEnd
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.Volume
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.AvatarGroup
import io.kontour.ui.components.display.AvatarSize
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A call in progress, over the whole window.
 *
 * Over the navigation too, rather than inside it: a call is not a place in the
 * app you go between tabs from, and a tab bar under it would say it was. Its
 * one way out is End, which is also what back does.
 *
 * The status says "Calling…" and then "Connected", once. No ticking timer: a
 * clock that changes every second is a page that never settles, in a golden or
 * in a test waiting for it to.
 */
@Composable
internal fun CallPage(chat: Chat, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(ConnectTime)
        connected = true
    }

    // The raised ground, not a colour of its own: the page is the theme's in
    // light, dark and high contrast alike, and still reads as above the app.
    Surface(modifier = modifier.fillMaxSize(), containerColour = Theme.colours.surfaceRaised) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.allEdges)
                .padding(Theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (chat.kind == ChatKind.Group) {
                AvatarGroup(names = chat.members, size = AvatarSize.Large)
                Spacer(Modifier.height(Theme.spacing.lg))
            } else {
                Avatar(name = chat.name, size = CallAvatar)
                Spacer(Modifier.height(Theme.spacing.lg))
            }
            Text(chat.name, style = Theme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                when {
                    !connected -> "Calling…"
                    muted -> "Connected · muted"
                    else -> "Connected"
                },
                modifier = Modifier.padding(top = Theme.spacing.xxs),
                style = Theme.typography.bodyMedium,
                colour = Theme.colours.contentMuted,
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mute is the slashed microphone, drawn on, rather than a
                // second glyph: the off state of a microphone is the same
                // thing with a line through it.
                IconToggleButton(
                    checked = muted,
                    onCheckedChange = { muted = it },
                    icon = Tabler.Outline.Microphone,
                    contentDescription = "Mute",
                    strikethrough = true,
                    size = ButtonSize.Large,
                )
                IconToggleButton(
                    checked = speaker,
                    onCheckedChange = { speaker = it },
                    icon = Tabler.Outline.Volume,
                    contentDescription = "Speaker",
                    size = ButtonSize.Large,
                )
                IconToggleButton(
                    checked = video,
                    onCheckedChange = { video = it },
                    icon = Tabler.Outline.Video,
                    contentDescription = "Video",
                    size = ButtonSize.Large,
                )
            }
            Button(
                onClick = onEnd,
                modifier = Modifier.padding(top = Theme.spacing.lg),
                variant = ButtonVariant.Destructive,
                size = ButtonSize.Large,
            ) {
                +Tabler.Outline.PhoneEnd
                +"End call"
            }
        }
    }
}

/** How long "Calling…" shows before the other end picks up. */
private val ConnectTime = 2_000.milliseconds

/** Bigger than the largest stock size: on a call the face is the page. */
private val CallAvatar = AvatarSize(112.dp)
