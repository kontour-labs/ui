package io.kontour.ui.components.list

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.a11y.TouchTargetDefaults
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.verticalDragOwning
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.TooltipOverlay
import io.kontour.ui.theme.Theme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What an [IndexScroller]'s labels are drawn in.
 *
 * @param label A label with something under it.
 * @param current The label of the section at the top of the list, which is also
 *   drawn bold, so it is not told apart by colour alone.
 * @param empty A label with nothing under it. A press on one still goes to the
 *   next section that has something.
 * @param disabled Every label, while the scroller is disabled.
 */
@Immutable
data class IndexScrollerColours(
    val label: Color,
    val current: Color,
    val empty: Color,
    val disabled: Color,
)

/** Defaults for [IndexScroller]. */
object IndexScrollerDefaults {
    /**
     * How wide the rail's letters are drawn, against the list's end edge. Pad
     * the list's end by this — `contentPadding = PaddingValues(end = Width)` —
     * so its rows end clear of the letters. The rail's touch area is wider,
     * over the rows' ends, as a finger needs.
     */
    val Width: Dp get() = IndexRailWidth

    /**
     * The muted ink for a label, the accent for the current one, and the
     * disabled grey for a label with nothing under it — except at high
     * contrast, where an empty label keeps the muted ink, because it can still
     * be pressed and has to be read.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        label: Color = Theme.colours.contentMuted,
        current: Color = Theme.colours.primary,
        empty: Color = if (highContrast()) Theme.colours.contentMuted else Theme.colours.contentDisabled,
        disabled: Color = Theme.colours.contentDisabled,
    ): IndexScrollerColours = IndexScrollerColours(label, current, empty, disabled)
}

/**
 * A rail of letters beside a long list: press one, or run a finger down them,
 * and the list jumps to the section that starts there.
 *
 * ```kotlin
 * val formats = Theme.formats
 * val alphabet = IndexAlphabet.forLocale(Theme.locale)
 * val indexed = remember(stops, alphabet, formats) { stops.indexedBy(alphabet, formats) { it.name } }
 * val list = rememberLazyListState()
 *
 * Box {
 *     LazyColumn(state = list, contentPadding = PaddingValues(end = IndexScrollerDefaults.Width)) {
 *         indexed.groups.forEach { group ->
 *             item { SectionHeader { +group.label } }
 *             items(group.items) { StopRow(it) }
 *         }
 *     }
 *     IndexScroller(list, indexed.sections, Modifier.align(Alignment.CenterEnd), alphabet = alphabet)
 * }
 * ```
 *
 * Asked for as an A–Z index scroller, as a contacts list has.
 *
 * **Every label of the alphabet is on the rail**, the ones with nothing under
 * them dimmed: the rail is a map of the whole alphabet, and a letter that
 * vanished when its last name was deleted would move every letter below it. A
 * press on an empty one goes to the next section that has something. The last
 * label is the alphabet's [IndexAlphabet.other], `#`, for whatever no letter
 * took.
 *
 * **On a short window the rail folds**, a dot standing for each run of letters
 * it has no room for, with the first and the last always shown. The finger
 * still reaches every letter: running down a dot passes through the letters it
 * stands for, a tick for each, and the bubble beside the finger says which.
 *
 * **The bubble** shows the letter under the finger, large, beside the rail —
 * the finger is over the one on the rail. A tick is felt as the finger crosses
 * from one letter to the next.
 *
 * The list moves at once, with no scroll animation: a jump of a hundred rows is
 * not motion anybody needs to watch, and the finger is still moving.
 *
 * [sections] says where each label's section starts in the list: what
 * [indexedBy] gives, or your own, in the alphabet's order. The current label is
 * the section the list's first visible item is in.
 *
 * Place it over the list, at its end edge: `Modifier.align(Alignment.CenterEnd)`
 * in a `Box`, and `fillMaxHeight()` too to centre the labels down the whole
 * height. It is as wide as a touch target, for a finger, with the labels drawn
 * against the end edge in [IndexScrollerDefaults.Width]; right to left, that
 * edge is on the left. Pad the list's end by that width so its rows end clear
 * of the letters.
 *
 * @param state The list it scrolls.
 * @param sections Where each section starts in [state]'s list.
 * @param enabled When false the rail draws in the disabled grey and does nothing.
 * @param alphabet The labels on the rail. The reader's script, then Latin, then
 *   `#` by default; see [IndexAlphabet].
 * @param interactionSource Its drags, presses and focus.
 */
@Composable
fun IndexScroller(
    state: LazyListState,
    sections: List<IndexSection>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alphabet: IndexAlphabet = IndexAlphabet.forLocale(Theme.locale),
    colours: IndexScrollerColours = IndexScrollerDefaults.colours(),
    interactionSource: MutableInteractionSource? = null,
) {
    val labels = remember(alphabet) { alphabet.labels + alphabet.other }
    val landing = remember(labels, sections) { landingSections(labels, sections) }
    val filled = remember(labels, sections) {
        val present = sections.mapTo(HashSet()) { it.label }
        BooleanArray(labels.size) { labels[it] in present }
    }
    val currentSection by remember(state, sections) {
        derivedStateOf { sectionAt(sections, state.firstVisibleItemIndex) }
    }
    val at = currentSection
    val currentLabel = if (at < 0) -1 else labels.indexOf(sections[at].label)

    val strings = Theme.strings
    val style = Theme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val plain = remember(labels, style, measurer) { labels.map { measurer.measure(it, style) } }
    val bold = remember(labels, style, measurer) {
        labels.map { measurer.measure(it, style.copy(fontWeight = FontWeight.Bold)) }
    }

    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val modality = LocalInputModality.current
    val touch = if (modality.needsLargeTargets) Theme.sizing.minTouchTarget else TouchTargetDefaults.PointerMinimum
    val pad = Theme.spacing.xs
    val dotRadius = with(density) { (IndexDotSize / 2).toPx() }
    val inset = Theme.spacing.xxs

    val geometry = remember { RailGeometry() }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val ticker = rememberDetentTicker()
    var touched by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    val latestLanding by rememberUpdatedState(landing)
    val latestSections by rememberUpdatedState(sections)
    fun jumpToSection(index: Int) {
        val all = latestSections
        if (all.isEmpty()) return
        state.requestScrollToItem(all[index.coerceIn(all.indices)].itemIndex)
    }
    fun pick(y: Float) {
        val label = labelAt(y, geometry.slots, geometry.top, geometry.slotPx)
        if (label < 0) return
        ticker.at(label)
        if (label != touched) {
            touched = label
            val target = latestLanding.getOrElse(label) { -1 }
            if (target >= 0) jumpToSection(target)
        }
    }

    val spoken: (String) -> String = { if (it == alphabet.other) strings.otherSection else it }
    val lastSection = sections.lastIndex

    Layout(
        modifier = modifier
            .semantics {
                contentDescription = strings.sectionIndex
                if (!enabled) disabled()
                if (sections.isNotEmpty()) {
                    val now = at.coerceAtLeast(0)
                    stateDescription = spoken(sections[now].label)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = now.toFloat(),
                        range = 0f..max(lastSection, 1).toFloat(),
                        steps = (lastSection - 1).coerceAtLeast(0),
                    )
                    // Withheld rather than marked when disabled, as a slider's
                    // are: a rail that looks inert must not move the list.
                    if (enabled) {
                        setProgress { target ->
                            jumpToSection(target.roundToInt())
                            true
                        }
                        customActions = buildList {
                            if (now < lastSection) {
                                add(CustomAccessibilityAction(strings.nextSection) { jumpToSection(now + 1); true })
                            }
                            if (now > 0) {
                                add(CustomAccessibilityAction(strings.previousSection) { jumpToSection(now - 1); true })
                            }
                        }
                    }
                }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown || sections.isEmpty()) return@onKeyEvent false
                val target = when (event.key) {
                    Key.DirectionDown -> (at + 1).coerceAtMost(lastSection)
                    Key.DirectionUp -> (at - 1).coerceAtLeast(0)
                    Key.MoveHome -> 0
                    Key.MoveEnd -> lastSection
                    else -> return@onKeyEvent false
                }
                jumpToSection(target)
                true
            }
            .focusable(enabled, interactions)
            .focusRing(interactions, Theme.shapes.capsule, enabled = enabled)
            .pointerCursor(Cursor.Pointer, enabled)
            .verticalDragOwning(
                enabled = enabled,
                interactionSource = interactions,
                scope = scope,
                claimsOn = DragClaim.Press,
                onStart = { offset ->
                    fingerY = offset.y
                    ticker.reset()
                    pick(fingerY)
                },
                onDelta = { delta ->
                    fingerY += delta
                    pick(fingerY)
                },
                onEnd = {
                    touched = -1
                    ticker.reset()
                },
            )
            .onGloballyPositioned { geometry.coordinates = it }
            .drawBehind {
                val slots = geometry.slots
                val strip = geometry.stripWidth
                val centreX = if (rtl) strip / 2f else size.width - strip / 2f
                slots.forEachIndexed { i, slot ->
                    val centreY = geometry.top + geometry.slotPx * (i + 0.5f)
                    when (slot) {
                        is IndexSlot.Label -> {
                            val index = slot.index
                            val isCurrent = index == currentLabel
                            val text: TextLayoutResult = if (isCurrent) bold[index] else plain[index]
                            val colour = when {
                                !enabled -> colours.disabled
                                isCurrent -> colours.current
                                filled[index] -> colours.label
                                else -> colours.empty
                            }
                            drawText(
                                text,
                                color = colour,
                                topLeft = Offset(centreX - text.size.width / 2f, centreY - text.size.height / 2f),
                            )
                        }
                        is IndexSlot.Dot -> drawCircle(
                            color = if (enabled) colours.label else colours.disabled,
                            radius = dotRadius,
                            center = Offset(centreX, centreY),
                        )
                    }
                }
            },
    ) { _, constraints ->
        val slotPx = plain.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val padPx = pad.toPx()
        val strip = max(IndexRailWidth.toPx(), (bold.maxOfOrNull { it.size.width } ?: 0) + inset.toPx() * 2)
        val natural = ceil(labels.size * slotPx + padPx * 2).toInt()
        val height = natural.coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = max(strip, touch.toPx()).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val fits = if (slotPx <= 0f) labels.size else ((height - padPx * 2) / slotPx).toInt()
        val slots = indexSlots(labels.size, fits)
        geometry.slotPx = slotPx
        geometry.stripWidth = strip
        geometry.top = (height - slots.size * slotPx) / 2f
        geometry.slots = slots
        layout(width, height) {}
    }

    // Beside the finger, the letter it is over, large. Composed only while a
    // finger is on the rail, so a rail nobody is touching needs no host.
    val shown = touched
    if (enabled && shown >= 0) TooltipOverlay(
        visible = true,
        anchor = { geometry.slotRect(shown, rtl) },
        content = { Text(labels.getOrElse(shown) { "" }, style = Theme.typography.headlineMedium) },
        modifier = Modifier,
        side = OverlaySide.Start,
        alignment = OverlayAlignment.Centre,
        // Held until the finger lifts, however long that is: the touch
        // tooltip's own timeout is for a tooltip nobody is still touching.
        onDismissRequest = {},
    )
}

/** The rail's labels, at their visual width; the rail is a touch target wide. */
private val IndexRailWidth: Dp = 20.dp

/** A dot standing for the labels the rail has no room for. */
private val IndexDotSize: Dp = 4.dp

/**
 * Where the rail put its slots at its last measure, for the drag and the
 * bubble to find them. Written by the measure and read by the draw, so the
 * slots are state; the rest change only with them.
 */
@Stable
private class RailGeometry {
    var slots: List<IndexSlot> by mutableStateOf(emptyList())
    var top = 0f
    var slotPx = 0f
    var stripWidth = 0f
    var coordinates: LayoutCoordinates? = null

    /** The slot showing [label], or the dot standing for it, in the root's coordinates. */
    fun slotRect(label: Int, rtl: Boolean): Rect? {
        val coords = coordinates?.takeIf { it.isAttached } ?: return null
        val slot = slots.indexOfFirst {
            when (it) {
                is IndexSlot.Label -> it.index == label
                is IndexSlot.Dot -> label in it.first..it.last
            }
        }
        if (slot < 0) return null
        val left = if (rtl) 0f else coords.size.width - stripWidth
        val origin = coords.localToRoot(Offset(left, top + slotPx * slot))
        return Rect(origin, Size(stripWidth, slotPx))
    }
}

/**
 * The label under [y]: a shown label's own, or for a dot, the one of the run it
 * stands for that [y] has got to, so a finger running down a dot passes through
 * each in turn. Above the first slot is the first label and below the last the
 * last. -1 with no slots.
 */
internal fun labelAt(y: Float, slots: List<IndexSlot>, top: Float, slotPx: Float): Int {
    if (slots.isEmpty() || slotPx <= 0f) return -1
    val position = ((y - top) / slotPx).coerceIn(0f, slots.size - 0.001f)
    val i = position.toInt()
    return when (val slot = slots[i]) {
        is IndexSlot.Label -> slot.index
        is IndexSlot.Dot -> {
            val run = slot.last - slot.first + 1
            slot.first + ((position - i) * run).toInt().coerceAtMost(run - 1)
        }
    }
}

/**
 * For each of [labels], the section a press on it goes to: its own, or the
 * next after it that has something, or — past the last — the last. -1 for every
 * label when there are no sections. A section whose label is not on the rail
 * counts as after all of them.
 */
internal fun landingSections(labels: List<String>, sections: List<IndexSection>): IntArray {
    if (sections.isEmpty()) return IntArray(labels.size) { -1 }
    val positions = sections.map { section -> labels.indexOf(section.label).let { if (it < 0) labels.size else it } }
    return IntArray(labels.size) { label ->
        positions.indexOfFirst { it >= label }.let { if (it < 0) sections.lastIndex else it }
    }
}

/** The section [firstVisible] is in: the last that starts at or before it, or -1 before the first. */
internal fun sectionAt(sections: List<IndexSection>, firstVisible: Int): Int =
    sections.indexOfLast { it.itemIndex <= firstVisible }
