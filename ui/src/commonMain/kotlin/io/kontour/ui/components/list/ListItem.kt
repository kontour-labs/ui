package io.kontour.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * Where an item sits within a group, which decides which of its corners round.
 *
 * A list of settings reads as one object with rows in it, not as a stack of
 * separate cards — so only the outside corners of the group are rounded, and the
 * ones facing a neighbour are square.
 */
@Immutable
enum class ListItemPosition {
    /** The only item. All four corners rounded. */
    Only,

    /** The first of several. Top corners rounded. */
    First,

    /** Between two others. Square. */
    Middle,

    /** The last of several. Bottom corners rounded. */
    Last,
    ;

    companion object {
        /**
         * Where item [index] of [count] sits.
         *
         * ```kotlin
         * itemsIndexed(stops) { index, stop ->
         *     ListItem(
         *         onClick = { open(stop) },
         *         position = ListItemPosition.of(index, stops.size),
         *     ) { +stop.name }
         * }
         * ```
         */
        fun of(index: Int, count: Int): ListItemPosition = when {
            count <= 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/**
 * Resolves this [ListItemPosition] against a corner radius.
 *
 * Pure, and tested, because an off-by-one here is the kind of thing that looks
 * fine on a three-item list in the catalog and wrong on every one-item list in
 * the app.
 */
fun ListItemPosition.shape(shape: CornerBasedShape, square: Dp = 4.dp): Shape {
    val flat = androidx.compose.foundation.shape.CornerSize(square)
    return when (this) {
        ListItemPosition.Only -> shape
        ListItemPosition.First -> shape.copy(bottomStart = flat, bottomEnd = flat)
        ListItemPosition.Middle -> shape.copy(
            topStart = flat,
            topEnd = flat,
            bottomStart = flat,
            bottomEnd = flat,
        )

        ListItemPosition.Last -> shape.copy(topStart = flat, topEnd = flat)
    }
}

object ListItemDefaults {
    /** Corner radius on a group's outside edges. */
    val Shape: CornerBasedShape @Composable get() = Theme.shapes.medium

    /** Gap between items in a group. */
    val Spacing: Dp = 2.dp

    /**
     * The rounding on the two corners that face each other across a seam.
     *
     * Derived from [Spacing] rather than set beside it, because the two are one
     * decision: the notch between two rows should be as deep as the gap between
     * them. At 4dp against a 2dp gap it read deeper than the gap it was
     * describing, which is the same near-miss that makes any two nested shapes
     * look mismatched.
     */
    val InnerCorner: Dp = Spacing

    /**
     * The floor for a one-line row, and for a two-line one.
     *
     * These are what make a row as tall as it is — not the padding, which is the
     * thing that gets blamed. At 12dp vertical a single line of text came to
     * 44dp and the 56dp floor lifted it the rest of the way, so trimming the
     * padding to 8dp changed the row's height by *nothing*: it only ever showed
     * up on a three-region row, where the content finally cleared the floor.
     *
     * 56 and 72 are Material's numbers, and they are sized for a row with a
     * 40dp avatar in it. The lists this is for are mostly a line of text and a
     * time, so they come down to the touch-target floor and one step above it,
     * which is where the padding starts doing the work instead.
     */
    val MinHeight: Dp = 48.dp
    val TwoLineMinHeight: Dp = 64.dp
}

/**
 * One row of a list.
 *
 * ```kotlin
 * ListItem(
 *     onClick = { open(stop) },
 *     position = ListItemPosition.of(index, stops.size),
 * ) {
 *     leading { +Tabler.Outline.Train }
 *     +"Perth Underground"
 *     supporting { +"Platform 2 · Joondalup line" }
 *     trailing { +"4 min" }
 * }
 * ```
 *
 * The row owns the interaction, not the controls inside it. A bare control with
 * a `Text` beside it gives the user a small target and gives a screen reader two
 * nodes for one choice — the same reasoning as
 * [io.kontour.ui.components.selection.SelectionRow], which is the specialisation
 * of this for a row that toggles something.
 *
 * @param position Which corners round. See [ListItemPosition]; a group of rows
 *   should read as one object, not a stack of separate cards.
 * @param selected Marks the current row in a list that picks one. Requires
 *   [role] to say which kind of choice it is.
 * @param role What a screen reader announces. `Role.Button` for a row that
 *   navigates, `Role.RadioButton` for one of a set, `Role.Checkbox` for one that
 *   toggles.
 */
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    role: Role = Role.Button,
    position: ListItemPosition = ListItemPosition.Only,
    shape: Shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner),
    /**
     * The row's own ground. Sunken rather than [io.kontour.ui.theme.ColorScheme.surface]
     * by default, because in this scheme `surface` and `background` are the same
     * white — a row drawn on `surface` is invisible on a page, and a group of
     * them reads as loose text rather than as one object with rows in it.
     */
    containerColor: Color = Theme.colors.surfaceSunken,
    selectedContainerColor: Color = Theme.colors.accent.container,
    contentColor: Color = Theme.colors.content,
    /**
     * Unspecified derives it from the content: a row with an [ListItemScope.overline]
     * or [ListItemScope.supporting] line is taller. It used to be a default
     * expression over the string parameters, which a builder cannot be — nothing
     * knows what the row holds until the builder has run.
     */
    minHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val container = when {
        !enabled -> colors.surfaceSunken.copy(alpha = 0.5f)
        selected -> selectedContainerColor
        else -> containerColor
    }
    val content = when {
        !enabled -> colors.contentDisabled
        selected -> colors.accent.onContainer
        else -> contentColor
    }

    val interactive = onClick != null && enabled
    // Note the two conditions. A row with no `onClick` is not a control and gets
    // no click modifier at all; a row *with* one that is disabled is still a
    // control, and keeps the modifier with `enabled = false`. Dropping it would
    // block the callback silently — the row would announce as plain text, with
    // no role and no "disabled", and a screen-reader user would keep trying it.
    val clickModifier = when {
        onClick == null -> Modifier
        selected || role == Role.RadioButton -> Modifier.selectable(
            selected = selected,
            interactionSource = interactions,
            // A whole row flinching is too much movement; the tonal wash is the
            // feedback here.
            indication = kontourIndication(shape, pressScale = 1f),
            enabled = enabled,
            role = role,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
        )

        else -> Modifier.clickable(
            interactionSource = interactions,
            indication = kontourIndication(shape, pressScale = 1f),
            enabled = enabled,
            role = role,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
        )
    }

    val resolvedMinHeight = when {
        minHeight != Dp.Unspecified -> minHeight
        slots.isMultiLine -> ListItemDefaults.TwoLineMinHeight
        else -> ListItemDefaults.MinHeight
    }

    // Published so a control in a slot can show the row's press — a `Switch` in
    // a trailing slot has no callback of its own and would otherwise never see
    // the tap that the row just handled.
    CompositionLocalProvider(LocalRowInteractionSource provides interactions) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .defaultMinSize(minHeight = resolvedMinHeight)
                // Keyed on being a control at all, not on being enabled — a row that
                // changed height when it greyed out would jump the list around it.
                .then(if (onClick != null) Modifier.minimumTouchTarget() else Modifier)
                .focusRing(interactions, shape, enabled = interactive)
                .clip(shape)
                .background(container, shape)
                // `surfaceSunken` on `background` is 1.06:1 at the high-contrast
                // light tier, so a group of rows reads as loose text rather than as
                // one object with rows in it — which is the exact thing the sunken
                // ground exists to prevent, failing at the tier that needs it most.
                .then(contrastEdge()?.let { Modifier.border(it, shape) } ?: Modifier)
                .then(clickModifier)
                // `xs`, not `sm`. The 12dp version put 24dp of air around a
                // single line of text inside a row whose minimum is already 56dp,
                // so the padding was never what set the height — it only pushed a
                // two-line row taller than it needed to be. `MinHeight` holds the
                // floor for the one-line case, which is what it is for.
                .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val muted = if (enabled) colors.contentMuted else colors.contentDisabled

            slots.leading?.let { leading ->
                Box(contentAlignment = Alignment.Center) {
                    // Muted and `iconLarge`, which is what the old `leadingIcon`
                    // parameter did. Anything that is not a bare glyph — an avatar, a
                    // checkbox — sets its own colours and is unaffected.
                    ProvideContentColor(muted) {
                        ContentSlot(iconSize = Theme.sizing.iconLarge, content = leading)
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                slots.overline?.let { overline ->
                    ProvideContentColor(muted) {
                        ProvideTextStyle(Theme.typography.labelSmall) {
                            ContentSlot(maxLines = 1, content = overline)
                        }
                    }
                }
                slots.label?.let { label ->
                    ProvideContentColor(content) {
                        ProvideTextStyle(Theme.typography.bodyMedium) {
                            ContentSlot(maxLines = 2, content = label)
                        }
                    }
                }
                slots.supporting?.let { supporting ->
                    ProvideContentColor(muted) {
                        ProvideTextStyle(Theme.typography.bodySmall) {
                            ContentSlot(maxLines = 2, content = supporting)
                        }
                    }
                }
            }

            slots.trailing?.let { trailing ->
                Box(contentAlignment = Alignment.Center) {
                    ContentSlot(content = trailing)
                }
            }
        }
    }
}

/**
 * A titled group of [ListItem]s.
 *
 * ```kotlin
 * ListSection(title = { +"Notifications" }) {
 *     SelectionRow(delays, viewModel::setDelays, Role.Switch) { +"Delays" }
 *     SelectionRow(cancels, viewModel::setCancels, Role.Switch) { +"Cancellations" }
 * }
 * ```
 *
 * Spaces its children and marks the title as a heading, so a screen reader can
 * jump between sections. It does *not* set each child's [ListItemPosition] —
 * doing that would mean walking the composed children, which Compose has no way
 * to do. Use [listPositions] or [ListItemPosition.of] at the call site.
 *
 * @param description Sits under the title, above the rows. For what the group
 *   *is*.
 * @param footer Sits under the rows. For what the setting *does* — the sentence
 *   a settings screen puts below a switch to explain the consequence of it. A
 *   slot like [description] because it is the same kind of thing, and the two
 *   should not read as different mechanisms.
 * @param action Holds *controls* rather than the section's own text, which is
 *   why it is a plain `RowScope` and the three above are content slots.
 */
@Composable
fun ListSection(
    modifier: Modifier = Modifier,
    title: (@Composable ContentScope.() -> Unit)? = null,
    description: (@Composable ContentScope.() -> Unit)? = null,
    footer: (@Composable ContentScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    spacing: Dp = ListItemDefaults.Spacing,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title != null || action != null) {
            SectionHeader(
                description = description,
                action = action,
                // An action with no title used to draw an empty `Text`, which
                // took a line's height to say nothing. An empty slot draws
                // nothing at all.
                title = title ?: {},
            )
        }
        content()
        if (footer != null) {
            Box(
                Modifier.padding(
                    start = Theme.spacing.md,
                    end = Theme.spacing.md,
                    top = Theme.spacing.xxs,
                )
            ) {
                ProvideTextStyle(Theme.typography.bodySmall) {
                    ProvideContentColor(Theme.colors.contentMuted) {
                        ContentSlot(content = footer)
                    }
                }
            }
        }
    }
}

/** A heading above a group of rows. */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    description: (@Composable ContentScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    title: @Composable ContentScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Theme.spacing.md,
                end = Theme.spacing.xs,
                top = Theme.spacing.md,
                bottom = Theme.spacing.xxs,
            ),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // `mergeDescendants` so the heading node carries the title's text,
            // the way it did when the title was a `Text` wearing the modifier
            // itself. A heading a screen reader can find but not read is worse
            // than no heading.
            Box(Modifier.semantics(mergeDescendants = true) { heading() }) {
                ProvideTextStyle(Theme.typography.labelMedium) {
                    ProvideContentColor(Theme.colors.contentMuted) {
                        ContentSlot(content = title)
                    }
                }
            }
            if (description != null) {
                ProvideTextStyle(Theme.typography.bodySmall) {
                    ProvideContentColor(Theme.colors.contentSubtle) {
                        ContentSlot(content = description)
                    }
                }
            }
        }
        action?.invoke(this)
    }
}

/**
 * The positions for a list of [count] items.
 *
 * ```kotlin
 * val positions = listPositions(stops.size)
 * stops.forEachIndexed { index, stop ->
 *     ListItem(position = positions[index], …) { +stop.name }
 * }
 * ```
 *
 * Equivalent to calling [ListItemPosition.of] per item; useful when the list is
 * short enough to build eagerly and you would rather not repeat the size.
 */
fun listPositions(count: Int): List<ListItemPosition> =
    List(count) { index -> ListItemPosition.of(index, count) }

/**
 * A row with an icon, a label and a value — the settings-screen shape.
 *
 * A thin wrapper over [ListItem], here because the alternative is every settings
 * screen assembling the same four arguments slightly differently.
 */
@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    position: ListItemPosition = ListItemPosition.Only,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    ListItem(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        position = position,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * The value a setting is currently at, drawn at the trailing edge.
 *
 * `SettingRow` used to take this as a `value: String?` whose whole job was to
 * build a default for its `trailing` slot — the parallel-parameter shape the slot
 * API exists to delete. It is a one-line helper now, and it composes with
 * anything else the trailing edge wants.
 *
 * ```kotlin
 * SettingRow(onClick = ::openUnits) {
 *     +"Units"
 *     trailing { settingValue("Kilometres") }
 * }
 * ```
 */
@Composable
fun ContentScope.settingValue(value: String) {
    Text(
        text = value,
        style = Theme.typography.bodyMedium,
        color = Theme.colors.contentMuted,
        maxLines = 1,
    )
}
