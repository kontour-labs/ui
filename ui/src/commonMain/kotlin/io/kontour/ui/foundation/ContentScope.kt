package io.kontour.ui.foundation

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

/**
 * The `+` vocabulary: what you can drop into a slot without writing a
 * composable.
 *
 * ```kotlin
 * Button(onClick = ::save) {
 *     +Tabler.Outline.Check
 *     +"Save"
 * }
 * ```
 *
 * Every slot in the library takes content, because a `String` cannot carry a
 * second colour, an inline tag, or whatever the design wants next — and a
 * component that takes a `String` grows a parallel slot beside it the first time
 * anyone needs one of those. `SettingRow` had reached that point already: a
 * `value: String?` whose only job was to build a default for its `trailing`
 * slot.
 *
 * Slots cost brevity, which is why Material call sites run long. This is what
 * buys it back. The common case is one character; anything richer is a normal
 * composable in the same block, because a slot is a slot.
 *
 * ### Style comes from the slot
 *
 * `+"…"` never says how to draw itself. Each slot wraps its content in
 * [ProvideTextStyle] and [ProvideContentColor], so the same `+` is `bodySmall`
 * and muted under a row's supporting text and the button's own label style
 * inside a button. That is the whole reason this can be one character: the call
 * site has nothing to decide.
 *
 * ### Four types, and no more
 *
 * [String], [AnnotatedString], [ImageVector], [Painter]. A fifth overload is the
 * point at which the call site should be writing a composable — the shorthand is
 * for the shape written nine times out of ten, not a second way to express
 * everything.
 *
 * An icon added with `+` is **decorative**: it takes no content description,
 * because the text beside it is the accessible name and a screen reader
 * announcing both says everything twice. For an icon that carries meaning of its
 * own, use [icon].
 */
@LayoutScopeMarker
@Stable
interface ContentScope {

    /** Text, in the slot's own style. */
    @Composable
    operator fun String.unaryPlus()

    /** Text that carries its own spans, merged over the slot's style. */
    @Composable
    operator fun AnnotatedString.unaryPlus()

    /** A decorative icon, tinted and sized by the slot. */
    @Composable
    operator fun ImageVector.unaryPlus()

    /** A decorative image, tinted and sized by the slot. */
    @Composable
    operator fun Painter.unaryPlus()

    /**
     * An icon that is the only thing saying what this is.
     *
     * The `+` form is deliberately decorative — see the class docs — so this is
     * the spelling for an icon with no text beside it. If you find yourself
     * reaching for it often, the component probably wants a real
     * `contentDescription` parameter instead.
     */
    @Composable
    fun icon(image: ImageVector, contentDescription: String)
}

/**
 * A [ContentScope] whose content is laid out in a row, so `Modifier.weight` and
 * `Modifier.align` still mean what they always did.
 *
 * Extending the scope the lambda already had is the rule every shorthand in the
 * library follows — see `docs/app/design-system/dsls.md`. It is why adopting one
 * has never broken a call site: a button body written against `RowScope` is a
 * button body written against this.
 */
@LayoutScopeMarker
@Stable
interface RowContentScope : ContentScope, RowScope

/**
 * @param iconSize How large `+icon` draws.
 * @param maxLines Where `+"…"` stops. A row's label wraps to two lines and a
 *   chip's to none, and that is the slot's business rather than the call site's —
 *   the same reason the style is.
 * @param overflow What a truncated `+"…"` does at the cut.
 *
 * All three are carried on the scope rather than in composition locals because
 * they belong to the slot, and a slot knows its own at the point it builds one.
 */
internal open class ContentScopeImpl(
    private val iconSize: Dp,
    private val maxLines: Int = Int.MAX_VALUE,
    private val overflow: TextOverflow = TextOverflow.Clip
) : ContentScope {

    @Composable
    override operator fun String.unaryPlus() =
        Text(this, maxLines = maxLines, overflow = overflow)

    @Composable
    override operator fun AnnotatedString.unaryPlus() =
        Text(this, maxLines = maxLines, overflow = overflow)

    @Composable
    override operator fun ImageVector.unaryPlus() =
        Icon(imageVector = this, contentDescription = null, size = iconSize)

    @Composable
    override operator fun Painter.unaryPlus() =
        Icon(painter = this, contentDescription = null, size = iconSize)

    @Composable
    override fun icon(image: ImageVector, contentDescription: String) =
        Icon(imageVector = image, contentDescription = contentDescription, size = iconSize)
}

internal class RowContentScopeImpl(
    row: RowScope,
    iconSize: Dp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) : ContentScopeImpl(iconSize, maxLines, overflow), RowContentScope, RowScope by row

/**
 * Runs [content] as a row-shaped slot.
 *
 * The `Row` itself belongs to the caller — this only supplies the scope — so a
 * component keeps control of its own arrangement and padding.
 */
@Composable
internal fun RowScope.contentScope(
    iconSize: Dp = Theme.sizing.iconMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    content: @Composable RowContentScope.() -> Unit
) {
    RowContentScopeImpl(this, iconSize, maxLines, overflow).content()
}

/** Runs [content] as a slot with no layout of its own. */
@Composable
internal fun ContentSlot(
    iconSize: Dp = Theme.sizing.iconMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    content: @Composable ContentScope.() -> Unit
) {
    ContentScopeImpl(iconSize, maxLines, overflow).content()
}
