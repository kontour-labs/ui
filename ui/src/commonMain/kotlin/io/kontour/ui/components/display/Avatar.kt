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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

/**
 * How big an avatar is drawn.
 *
 * ```kotlin
 * Avatar(name = "Aaron", size = AvatarSize.Medium)
 * Avatar(name = "Aaron", size = AvatarSize(32.dp))   // anything the layout needs
 * ```
 *
 * It was an `enum` of four — 24, 40, 56, 80 — and the answer to "we need 32"
 * was to add a fifth entry, then a sixth, and then to argue about what to call
 * the one between `Small` and `Medium`. A ladder that has to be enumerated grows
 * awkward names faster than it grows sizes. The five below are the ones worth
 * naming; every other diameter is one call away, and nothing renumbered, so
 * `AvatarSize.Medium` is the same 40dp it always was.
 */
@JvmInline
value class AvatarSize(val diameter: Dp) {

    /**
     * Initials, sized from the diameter.
     *
     * `diameter * 0.3125 + 2.5dp`, which reproduces the old hand-listed 10, 15
     * and 20 exactly. It is a line rather than a ratio because the old table was
     * one: the text-to-diameter *ratio* fell from 0.42 to 0.35 as the avatar
     * grew, which is the optical correction a small avatar needs to stay
     * readable, and a single multiplier would have thrown it away.
     *
     * The one value it does not reproduce is `XLarge`'s 28dp, which the line
     * puts at 27.5 — a rounding somebody did by hand, and half a dp.
     */
    internal val textSize: Dp get() = diameter * TextRatio + TextOffset

    companion object {
        /** 20dp. Inline in a line of text, or a dense list. */
        val XSmall = AvatarSize(20.dp)

        /** 24dp. A stack of participants, a compact row. */
        val Small = AvatarSize(24.dp)

        /** 40dp. The list-row default. */
        val Medium = AvatarSize(40.dp)

        /** 56dp. A header, a card. */
        val Large = AvatarSize(56.dp)

        /** 80dp. A profile screen. */
        val XLarge = AvatarSize(80.dp)

        private const val TextRatio = 0.3125f
        private val TextOffset = 2.5.dp
    }
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
) {
    val density = LocalDensity.current
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
            .background(if (image != null) Theme.colors.surfaceSunken else background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> androidx.compose.foundation.Image(
                painter = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.diameter).clip(Theme.shapes.pill),
            )

            // `size.textSize`, which has been sitting there computed and unread.
            //
            // The initials were `labelMedium` at every diameter, so a 20dp
            // avatar and an 80dp one drew the same 14sp letters — lost in the
            // large one and overflowing the small. `AvatarSize` has carried the
            // right number the whole time, with a paragraph explaining the line
            // it comes from; nothing called it.
            //
            // Derived from the diameter in `dp` rather than scaled from the
            // style in `sp`, which is the opposite of what a day number in a
            // calendar wants and right for the same reason: this is a glyph
            // inside a circle of fixed size, not text. The circle does not grow
            // with the user's font setting, so the letters inside it must not
            // either — and they are `clearAndSetSemantics` because the name is
            // announced by the avatar itself.
            name != null -> Text(
                text = name.initials(),
                style = Theme.typography.labelMedium.copy(
                    fontSize = with(density) { size.textSize.toSp() },
                    lineHeight = TextUnit.Unspecified,
                ),
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
                    // The ring that separates one overlapping avatar from the
                    // next. A `Modifier.border` at the call site, because that is
                    // all it ever was — the parameter carried a colour and hid a
                    // hardcoded 2dp and pill shape behind it.
                    modifier = Modifier.border(
                        AvatarGroupRing,
                        Theme.colors.surface,
                        Theme.shapes.pill,
                    ),
                    size = size,
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
        c.accent.solid,
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

/** The ring that keeps overlapping avatars legible against one another. */
private val AvatarGroupRing = 2.dp
