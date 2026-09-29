# `Text`

*Also on this page: `ProvideTextStyle`, `richText`, `markdownText`.*

The typographic primitive. Resolves its style and its colour from the theme
rather than taking them, so a paragraph inside a `Card` on a dark scheme needs
no arguments at all.

<!--sample:TextBasics-->
```kotlin
Text("Perth Underground", style = Theme.typography.titleMedium)

Text(
    "Platform 2 · Joondalup line",
    style = Theme.typography.bodySmall,
    colour = Theme.colours.contentMuted,
)

// The `AnnotatedString` overload is why there are two: a route number in
// the accent's text colour inside a sentence, without a second component
// and without breaking the line box.
Text(
    richText {
        +"The "
        tone(Tone.Accent, "950")
        +" leaves in 4 minutes."
    },
)
```

Two overloads, `String` and `AnnotatedString`. The second is what carries spans —
a route number in the accent colour inside a sentence — without a second
component or a second style.

Colour comes from `LocalContentColour`, which [`Surface`](surface.md) sets. That
chain is the reason a component can be dropped on a dark card and stay legible
without every child being told where it is.

## Rich text

`richText { }` builds the `AnnotatedString`, and every verb in it draws from the
theme:

<!--sample:RichTextBasics-->
```kotlin
// Each verb draws from the theme: the bold the type scale ships, the mono
// face on the sunken ground, a tone's own text colour.
Text(
    richText {
        +"The "; bold("950"); +" is running "; tone(Tone.Warning, "12 minutes late"); +". "
        +"Scan at the "; code("SmartRider"); +" reader as usual. "
        link("Replacement buses") { replacements() }
    },
)

// A string that already carries its emphasis — a translated one, most of
// all, where another language puts the bold word somewhere else.
Text(
    markdownText(
        "Services on the **Midland** line are *suspended* until 6pm. " +
            "[Timetables](https://transperth.wa.gov.au)",
    ),
)
```

| Verb | Draws |
|---|---|
| `bold`, `italic`, `underline`, `strikethrough` | the theme's bold weight — SemiBold, which the face ships — italic, a line under, a line through |
| `style(typography.x)` | a role from the type scale: its size, weight and spacing |
| `highlight` | a marker behind the words: the warning container's tint, or a colour given |
| `code` | the theme's mono face on `surfaceSunken`, never `FontFamily.Monospace` |
| `tone(Tone.X)` | the text colour a `Tag` of that tone is lettered in |
| `colour(colour)`, `gradient(brush)` | any colour, or a gradient across these words — the two that do not come from the theme |
| `link(text) { … }` | a link that calls you back |
| `link(text, url)` | a link the platform opens; only `http`, `https`, `mailto` and `tel` |
| `markdown(source)` | inline Markdown, into the same string |
| `+icon`, `icon(icon, description)` | an icon in the line, drawn by `Text` |

Each takes a `String` or a block, so `bold { +"950 "; italic("express") }` nests.
The string comes out **equal to itself** between compositions — the link
listener is remembered, the styles are values — so a recomposition does not lay
the text out again. Hand-built strings with a lambda in a `LinkAnnotation` are
the thing this replaces, because they are never equal and re-shape every frame.

### Size, marker, colour and gradient

<!--sample:RichTextStyles-->
```kotlin
// A size from the type scale, a marker behind what changed, an underline:
// each from the theme, as the verbs above are.
Text(
    richText {
        +"Next train in "; style(typography.titleLarge, "4 min"); +". "
        +"Tonight it leaves from "; highlight("platform 3"); +". "
        +"Tap on "; underline("and"); +" off."
    },
)

// A colour or a gradient a brand asks for, read before the builder because
// it is not composable. Nothing holds these to a contrast: for a colour that
// means something, `tone` is the one held to 4.5:1.
val brand = Theme.colours.brand
val sky = Theme.colours.info.solid
Text(
    richText { +"Meet "; gradient(listOf(brand, sky), "Kontour"); +", in "; colour(sky, "sky"); +"." },
    style = Theme.typography.headlineSmall,
)
```

`style` takes a role from `typography`, which the scope holds because the builder
is not composable and cannot read `Theme` itself. A role's line height belongs to
the paragraph, so a role much larger than the words around it sits in a line
spaced for them: give a size that big a line of its own.

An underline is what says *link* inside a paragraph, which is why every link has
one, so an underlined word that does nothing when pressed will be pressed. Use it
where a reader knows it is not a link — one word of an instruction, say — and
`bold` or `highlight` where they might not.

`colour` and `gradient` are the escape hatches. Nothing holds them to any contrast,
in either scheme or at high contrast, so they are for display — a brand's name, a
chart's key — and not for meaning, which is what `tone` is for. A gradient in a
Compose span is sized to the whole paragraph, so a gradient word in a sentence
would show only the slice under it; `Text` draws these words itself, and the
gradient starts where they start and ends where they end. A gradient that wraps
lies across the box around all of its lines.

### Icons in the line

An icon inside a sentence wraps with it, which an `Icon` in a `Row` beside the
text cannot do. Put it in the string, and `Text` draws it — no `inlineContent`
map to write:

<!--sample:RichTextIcons-->
```kotlin
// `+icon` for an icon the words beside it already explain: it wraps with
// them, takes their size and colour, and a screen reader skips it.
Text(richText { +Tabler.Outline.Clock; +" Every 15 minutes until 7pm" })

// `icon(…)` for one that says something they do not. Its description is
// read in its place in the sentence. Inside a tone, it takes the tone.
Text(
    richText {
        +"Route 950 "
        tone(Tone.Warning) { icon(Tabler.Outline.AlertTriangle, "disrupted"); +" 12 minutes late" }
    },
)
```

It is the text's height by default — `size = 1.em` — so it grows with the
reader's font scale as the words do, and it is drawn in the colour of the words
around it unless `tint` says otherwise. A caller's own `inlineContent` still
works beside it, for a key cap or a badge.

**`markdownText(source)` is for strings that already carry their emphasis**, and
above all translated ones: another language puts the bold word somewhere else,
and a bold span assembled around concatenated fragments cannot follow it. It
reads the inline half of Markdown — `**bold**`, `*italic*`, `` `code` ``,
`~~struck~~`, `[links](https://…)` and backslash escapes. A `#` or a `-` at the
start of a line is left as written, because headings and lists are layout. A
delimiter that never closes is shown as itself, and `snake_case` keeps its
underscores. The parse is remembered against the string.

**Text cannot be selected on its own.** Put reading a user may want to copy —
an address, a reference number, an article — inside a
[`SelectableText`](selectable-text.md).

---

## Accessibility

`Text` is the accessible content of nearly everything above it, so most of what
matters here is what it is *inside*: a component that draws its label with
`Text` and puts the name on the control is the pattern, and drawing a label
beside an unnamed control is the bug — Compose has no `labelledBy`, so a label
near a field is an unrelated node however close it is on screen.

Links inside an `AnnotatedString` use `LinkAnnotation`, so the platform gets real
link semantics — focusable, announced as a link, activated by the keyboard —
rather than a tap handler that works out which range was hit.

Never size type in `sp` computed from a `Dp`. `Theme.typography` scales with the
user's text setting; a literal does not, and 200% is the setting the
[accessibility page](../accessibility.md) promises to survive.

At high contrast `Text` takes the enhanced scheme's colours through
`LocalContentColour` and the theme, and nothing else about it changes; a colour
passed by hand is drawn as given. Links are underlined at every tier, so a link
is never told apart by colour alone.

`Text` does not move, so reduced motion changes nothing about it. Inside
[`Redacted`](redaction.md) its lines become a shimmering placeholder, and under
reduced motion the shimmer stops and leaves a still block.
