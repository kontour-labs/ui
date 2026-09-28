package io.kontour.ui.components.display

import io.kontour.ui.locale.bidiIsolated
import io.kontour.ui.locale.LocaleFormats
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.takeOrElse
import io.kontour.ui.foundation.LocalContainerColour
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.optionalBorder
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
 * ### Decorations
 *
 * Small things on the rim — asked for from a phone as "decorator icons in
 * corners":
 *
 * ```
 * Avatar(name = "Aaron") {
 *     status(Theme.colours.success.solid, "Online")
 *     count(3, at = AvatarCorner.TopEnd)
 * }
 * ```
 *
 * Each sits **on the circle's rim at 45°** in its corner, not in the corner of
 * the square round it: a dot in the square's corner floats clear of a round
 * picture. Each is sized from the avatar and ringed in the colour of whatever
 * the avatar is on, so it reads as laid over the picture rather than as part of
 * it. The avatar's announcement carries every decoration's description after the
 * name — "Aaron. Online. 3 new" — since a dot is drawn, not said.
 *
 * @param name Used for the initials and, by default, the announcement. Pass
 *   `null` for a genuinely anonymous avatar.
 * @param decorations Status dots, counts and glyphs on the rim. See
 *   [AvatarDecorationScope].
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    name: String? = null,
    image: Painter? = null,
    fallbackIcon: ImageVector? = null,
    size: AvatarSize = AvatarSize.Medium,
    contentDescription: String? = null,
    decorations: (AvatarDecorationScope.() -> Unit)? = null,
) {
    val placed = decorations?.let { AvatarDecorationScope().apply(it).items }.orEmpty()
    val base = contentDescription ?: name
    val strings = Theme.strings
    val formats = Theme.formats
    // A count with no words of its own says what a badge would.
    val said = placed.mapNotNull { decoration ->
        decoration.description ?: (decoration as? AvatarDecoration.Count)?.let { count ->
            if (count.value > count.max) {
                strings.moreThan(formats.quantity(count.max))
            } else {
                strings.newCount(formats.quantity(count.value))
            }
        }
    }
    val announcement = if (said.isEmpty()) base else strings.sentences(listOfNotNull(base) + said)
    if (placed.isEmpty()) {
        AvatarBody(modifier.announced(announcement), name, image, fallbackIcon, size)
        return
    }
    Layout(
        modifier = modifier.announced(announcement),
        content = {
            AvatarBody(Modifier, name, image, fallbackIcon, size)
            placed.forEach { decoration -> key(decoration) { Decoration(decoration, size) } }
        },
    ) { measurables, constraints ->
        val body = measurables.first().measure(constraints)
        val parts = measurables.drop(1).map { it.measure(Constraints()) }
        val centre = body.width / 2f
        // On the rim at 45°: the circle's radius along the diagonal.
        val along = centre * RimDiagonal
        layout(body.width, body.height) {
            body.placeRelative(0, 0)
            parts.forEachIndexed { index, part ->
                val corner = placed[index].at
                val x = centre + if (corner.atEnd) along else -along
                val y = centre + if (corner.atBottom) along else -along
                // Relative, so a start corner is on the right right to left.
                part.placeRelative((x - part.width / 2f).roundToInt(), (y - part.height / 2f).roundToInt())
            }
        }
    }
}

private fun Modifier.announced(announcement: String?): Modifier =
    semantics { if (announcement != null) this.contentDescription = announcement }

/** The avatar itself: its picture, initials or glyph, in its circle. */
@Composable
private fun AvatarBody(
    modifier: Modifier,
    name: String?,
    image: Painter?,
    fallbackIcon: ImageVector?,
    size: AvatarSize,
) {
    val density = LocalDensity.current
    val palette = avatarPalette()
    val background = if (name != null) {
        palette[name.stableIndex(palette.size)]
    } else {
        Theme.colours.surfaceSunken
    }
    val foreground = contentColourFor(
        background = background,
        light = Theme.colours.onPrimary,
        dark = Theme.colours.content,
    )

    val circle = Theme.shapes.pill
    Box(
        modifier = modifier
            .size(size.diameter)
            .clip(circle)
            .background(if (image != null) Theme.colours.surfaceSunken else background)
            // An avatar with no name and no picture is a well on the page —
            // 1.13:1 at the enhanced tier — so there it takes the tier's edge.
            .optionalBorder(if (name == null && image == null) contrastEdge() else null, circle),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> androidx.compose.foundation.Image(
                painter = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.diameter).clip(circle),
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
                text = name.initials(Theme.formats, Theme.strings.unknownInitials),
                style = Theme.typography.labelMedium.copy(
                    fontSize = with(density) { size.textSize.toSp() },
                    lineHeight = TextUnit.Unspecified,
                ),
                colour = foreground,
                modifier = Modifier.clearAndSetSemantics { },
            )

            fallbackIcon != null -> Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = Theme.colours.contentMuted,
                size = size.diameter / 2,
            )
        }
    }
}

/** A corner of an [Avatar], for a decoration: it sits on the rim at 45° there. */
enum class AvatarCorner(internal val atEnd: Boolean, internal val atBottom: Boolean) {
    TopStart(atEnd = false, atBottom = false),
    TopEnd(atEnd = true, atBottom = false),
    BottomStart(atEnd = false, atBottom = true),
    BottomEnd(atEnd = true, atBottom = true),
}

/**
 * What an [Avatar] carries on its rim: a status, a count, a glyph, or anything.
 *
 * Collected, then drawn over the avatar, each on the rim at 45° in its corner and
 * ringed in the colour of whatever the avatar sits on. Each `description` joins
 * the avatar's announcement; leave it null for one that says nothing new.
 */
@LayoutScopeMarker
@Stable
class AvatarDecorationScope internal constructor() {
    internal val items = mutableListOf<AvatarDecoration>()

    /** A dot: online, busy, away. A quarter of the avatar across. */
    fun status(colour: Color, description: String?, at: AvatarCorner = AvatarCorner.BottomEnd) {
        items += AvatarDecoration.Status(at, description, colour)
    }

    /**
     * A count — unread messages — as a [Badge] shows one, "99+" past [max]. Said as
     * the badge would say it unless given a [description].
     */
    fun count(
        value: Int,
        description: String? = null,
        at: AvatarCorner = AvatarCorner.TopEnd,
        max: Int = 99,
    ) {
        items += AvatarDecoration.Count(at, description, value, max)
    }

    /** A glyph in a small disc: a verified tick, a camera to change the picture, a role. */
    fun icon(
        icon: ImageVector,
        description: String?,
        at: AvatarCorner = AvatarCorner.BottomEnd,
        containerColour: Color = Color.Unspecified,
    ) {
        items += AvatarDecoration.Glyph(at, description, icon, containerColour)
    }

    /** Anything else, centred on the rim. Its size is its own; the ring is not drawn for it. */
    fun custom(at: AvatarCorner, description: String? = null, content: @Composable () -> Unit) {
        items += AvatarDecoration.Custom(at, description, content)
    }
}

/** One of an [AvatarDecorationScope]'s. */
internal sealed class AvatarDecoration(val at: AvatarCorner, val description: String?) {
    class Status(at: AvatarCorner, description: String?, val colour: Color) : AvatarDecoration(at, description)
    class Count(at: AvatarCorner, description: String?, val value: Int, val max: Int) : AvatarDecoration(at, description)
    class Glyph(at: AvatarCorner, description: String?, val icon: ImageVector, val containerColour: Color) :
        AvatarDecoration(at, description)
    class Custom(at: AvatarCorner, description: String?, val content: @Composable () -> Unit) :
        AvatarDecoration(at, description)
}

/**
 * One decoration, ringed in the ground colour: the page's, or the surface the
 * avatar is on. Silent — the avatar says what each one means.
 */
@Composable
private fun Decoration(decoration: AvatarDecoration, size: AvatarSize) {
    val ground = LocalContainerColour.current.takeOrElse { Theme.colours.background }
    val ring = Theme.sizing.borderWidthStrong
    val round = Theme.shapes.pill
    val edge = contrastEdge()
    val silent = Modifier.clearAndSetSemantics { }
    when (decoration) {
        is AvatarDecoration.Status -> Box(
            silent
                .clip(round)
                .background(ground)
                .padding(ring)
                .size((size.diameter * StatusShare).coerceIn(StatusMin, StatusMax))
                .clip(round)
                .background(decoration.colour)
                .optionalBorder(edge, round),
        )

        is AvatarDecoration.Count -> Box(silent.clip(Theme.shapes.capsule).background(ground).padding(ring)) {
            Badge(
                count = decoration.value,
                max = decoration.max,
                contentDescription = decoration.description,
            )
        }

        is AvatarDecoration.Glyph -> {
            val container = decoration.containerColour.takeOrElse { Theme.colours.accent.solid }
            val disc = (size.diameter * GlyphShare).coerceIn(GlyphMin, GlyphMax)
            Box(
                silent
                    .clip(round)
                    .background(ground)
                    .padding(ring)
                    .size(disc)
                    .clip(round)
                    .background(container)
                    .optionalBorder(edge, round),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = decoration.icon,
                    contentDescription = null,
                    tint = contentColourFor(container),
                    size = disc * GlyphIconShare,
                )
            }
        }

        is AvatarDecoration.Custom -> Box(silent) { decoration.content() }
    }
}

/** cos 45°: how far along each axis the rim is from the centre, as a share of the radius. */
private const val RimDiagonal: Float = 0.70710677f

/** A status dot, as a share of the avatar's diameter, and its limits. */
private const val StatusShare: Float = 0.25f
private val StatusMin: Dp = 8.dp
private val StatusMax: Dp = 16.dp

/** A glyph's disc, as a share of the avatar's diameter, and its limits; and the glyph in it. */
private const val GlyphShare: Float = 0.34f
private val GlyphMin: Dp = 14.dp
private val GlyphMax: Dp = 26.dp
private const val GlyphIconShare: Float = 0.64f

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
    val strings = Theme.strings
    val formats = Theme.formats
    val overlap = size.diameter / 3
    val density = LocalDensity.current

    // **Laid out as wide as it is drawn.** The overlap used to be
    // `Modifier.offset`, which moves what is drawn and not what is laid out, so the
    // group asked its row for five whole avatars and drew them in three and a half.
    // Reported from an iPhone as a line through the last avatar and no "+2": the
    // catalog's row could not give it that much on a phone, the count was measured
    // last against what was left — a sliver — and a pill-clipped sliver with a ring
    // round it is a line. So each avatar is measured at its own size, whatever is
    // left, and placed a step along; the group reports exactly the span it covers.
    Layout(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
                ?: if (names.size == 1) names.first() else strings.peopleCount(formats.quantity(names.size))
        },
        content = {
            shown.forEach { name ->
                Avatar(
                    name = name,
                    // The ring that separates one overlapping avatar from the
                    // next. A `Modifier.border` at the call site, because that is
                    // all it ever was — the parameter carried a colour and hid a
                    // hardcoded 2dp and pill shape behind it.
                    modifier = Modifier.border(
                        Theme.sizing.borderWidthStrong,
                        Theme.colours.surface,
                        Theme.shapes.pill,
                    ),
                    size = size,
                    contentDescription = null,
                )
            }
            if (overflow > 0) {
                val circle = Theme.shapes.pill
                Box(
                    Modifier
                        .size(size.diameter)
                        .clip(circle)
                        .background(Theme.colours.surfaceSunken)
                        // The edge first, so it draws over the page-coloured ring
                        // rather than under it: a modifier earlier in the chain
                        // draws after everything it wraps.
                        .optionalBorder(contrastEdge(), circle)
                        .border(Theme.sizing.borderWidthStrong, Theme.colours.surface, circle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = bidiIsolated(strings.moreCount(formats.quantity(overflow))),
                        // The same rule the initials follow, for the same reason —
                        // and this is the call site that made the first fix look
                        // half-done: a group of scaled initials with an unscaled
                        // "+2" beside them reads as one avatar in the wrong font.
                        style = Theme.typography.labelSmall.copy(
                            fontSize = with(density) { size.textSize.toSp() },
                            lineHeight = TextUnit.Unspecified,
                        ),
                        colour = Theme.colours.contentMuted,
                        // One line, always: a count that wraps inside a circle is
                        // clipped away rather than read.
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        // Each at its own size, not at whatever width is left: a squeezed avatar
        // is an oval, and a squeezed count is the line that was reported.
        val placeables = measurables.map { it.measure(Constraints()) }
        val step = (size.diameter - overlap).roundToPx()
        val span = (placeables.firstOrNull()?.width ?: 0) + step * (placeables.size - 1).coerceAtLeast(0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.constrainWidth(span), constraints.constrainHeight(height)) {
            // Later ones over earlier ones, as a hand of cards.
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(step * index, 0) }
        }
    }
}

/** The scheme's tones, used as avatar backgrounds so derived colours stay on-brand. */
@Composable
private fun avatarPalette(): List<Color> {
    val c = Theme.colours
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

/**
 * The first character of the first two words — "Aaron Smith" becomes "AS".
 *
 * A character as a reader counts one, not a UTF-16 unit: `Élodie` written with
 * a combining accent keeps its accent, `क्षितिज` starts with its conjunct, and an
 * emoji is not cut in half. Capitalised the way the name's language does it.
 */
private fun String.initials(formats: LocaleFormats, unknown: String): String =
    trim()
        .split(' ', '\t', '\u3000')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { formats.uppercase(formats.firstGrapheme(it)) }
        .ifEmpty { unknown }

/** The ring that keeps overlapping avatars legible against one another. */
