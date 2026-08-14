package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlin.math.absoluteValue

/** Standard avatar sizes. */
enum class AvatarSize(internal val diameter: Dp, internal val textSize: Dp) {
    Small(24.dp, 10.dp),
    Medium(40.dp, 15.dp),
    Large(56.dp, 20.dp),
    XLarge(80.dp, 28.dp),
}

/**
 * A person's picture, or a stand-in for one.
 *
 * ```
 * Avatar(name = "Aaron", image = painter)          // photo
 * Avatar(name = "Aaron")                            // initials on a derived colour
 * Avatar(name = null, fallbackIcon = Tabler.Outline.User)   // anonymous
 * ```
 *
 * Falls back in that order: image, then initials, then icon. The initials
 * background is **derived from the name**, so the same person is the same colour
 * everywhere in the app without anyone storing a colour — and two people in a
 * list are unlikely to collide.
 *
 * The derived colour comes from the scheme's status and accent tones rather than
 * from arbitrary hues, so it stays on-brand and its label contrast is already
 * known good.
 *
 * @param name Used for the initials and, by default, the announcement. Pass
 *   `null` for a genuinely anonymous avatar.
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    name: String? = null,
    image: Painter? = null,
    fallbackIcon: ImageVector? = null,
    size: AvatarSize = AvatarSize.Medium,
    contentDescription: String? = null,
    border: Color? = null,
) {
    val announcement = contentDescription ?: name
    val palette = avatarPalette()
    val background = if (name != null) {
        palette[name.stableIndex(palette.size)]
    } else {
        Theme.colors.surfaceSunken
    }
    val foreground = contentColorFor(
        background = background,
        light = Theme.colors.onPrimary,
        dark = Theme.colors.content,
    )

    Box(
        modifier = modifier
            .semantics {
                if (announcement != null) this.contentDescription = announcement
            }
            .size(size.diameter)
            .clip(Theme.shapes.pill)
            .background(if (image != null) Theme.colors.surfaceSunken else background)
            .then(
                if (border != null) {
                    Modifier.border(2.dp, border, Theme.shapes.pill)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> androidx.compose.foundation.Image(
                painter = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.diameter).clip(Theme.shapes.pill),
            )

            name != null -> Text(
                text = name.initials(),
                style = Theme.typography.labelMedium,
                color = foreground,
                modifier = Modifier.clearAndSetSemantics { },
            )

            fallbackIcon != null -> Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = Theme.colors.contentMuted,
                size = size.diameter / 2,
            )
        }
    }
}

/**
 * Overlapping avatars, for "these people are on this trip".
 *
 * Each avatar carries a ring in the surface colour so the overlap reads as depth
 * rather than as two shapes merging. Beyond [max] the remainder collapses into a
 * count.
 *
 * The group announces as one thing — "4 people" — rather than as four separate
 * nodes, since walking each avatar tells a screen-reader user nothing useful.
 */
@Composable
fun AvatarGroup(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.Small,
    max: Int = 4,
    contentDescription: String? = null,
) {
    if (names.isEmpty()) return
    val shown = names.take(max)
    val overflow = names.size - shown.size
    val overlap = size.diameter / 3

    Row(
        modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
                ?: if (names.size == 1) names.first() else "${names.size} people"
        }
    ) {
        shown.forEachIndexed { index, name ->
            Box(Modifier.offset(x = -overlap * index)) {
                Avatar(
                    name = name,
                    size = size,
                    border = Theme.colors.surface,
                    contentDescription = null,
                )
            }
        }
        if (overflow > 0) {
            Box(
                Modifier
                    .offset(x = -overlap * shown.size)
                    .size(size.diameter)
                    .clip(Theme.shapes.pill)
                    .background(Theme.colors.surfaceSunken)
                    .border(2.dp, Theme.colors.surface, Theme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = Theme.typography.labelSmall,
                    color = Theme.colors.contentMuted,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The scheme's tones, used as avatar backgrounds so derived colours stay on-brand. */
@Composable
private fun avatarPalette(): List<Color> {
    val c = Theme.colors
    return listOf(
        c.accent,
        c.success.solid,
        c.warning.solid,
        c.danger.solid,
        c.info.solid,
        c.primary,
    )
}

/**
 * A stable index for a name.
 *
 * Deliberately not `hashCode()`: Kotlin's String hash is stable across runs on
 * the JVM but is not guaranteed identical across Kotlin/Native and Kotlin/JS, so
 * the same person could be a different colour on iOS than on Android. This sum
 * is trivially stable everywhere.
 */
private fun String.stableIndex(buckets: Int): Int =
    (fold(0) { acc, ch -> acc + ch.code }).absoluteValue % buckets

/** First letter of the first two words — "Aaron Smith" becomes "AS". */
private fun String.initials(): String =
    trim()
        .split(' ', '\t')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
