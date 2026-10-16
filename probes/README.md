# Probes

Five tiny web apps, never published, that exist to be weighed.

The site's own figure in [`docs/check-bundle-size.py`](../docs/check-bundle-size.py)
is the library, the gallery and the documentation together. Every component and
every demo is reachable from it. So that figure says how fast the site grows, and
nothing about what an app that uses three components ships. Each probe here is
one app, compiled the way an app would be, and the differences between them are
the answers:

| Probe | What it draws | Measured against | What the difference is |
|---|---|---|---|
| `empty` | Compose's `BasicText`, no Kontour | | The floor: Compose itself |
| `theme` | `KontourTheme { Text }` | `empty` | What the theme costs before a single component is used |
| `button` | The theme and one `Button` | `theme` | One component. If this were most of the library, nothing would be stripping the rest |
| `icon` | The theme and one Tabler icon | `theme` | `Icon` and one vector. If this were thousands, the icons would not be stripped |
| `icons` | The theme and fifty Tabler icons | `icon` | Forty-nine vectors, so a fiftieth of it is what each further icon costs |

Only the application's own `.wasm` is weighed: the probe's code and every
declaration of `:ui` and Compose it can reach. Skia's renderer and the fonts are
the same bytes in every app, and they are counted once, by the site's figure.

## Running them

```sh
./gradlew :probes:empty:compileProductionExecutableKotlinWasmJsOptimize \
          :probes:theme:compileProductionExecutableKotlinWasmJsOptimize \
          :probes:button:compileProductionExecutableKotlinWasmJsOptimize \
          :probes:icon:compileProductionExecutableKotlinWasmJsOptimize \
          :probes:icons:compileProductionExecutableKotlinWasmJsOptimize
python3 docs/check-bundle-size.py --probes
python3 docs/probe-attribution.py probes/theme      # what is in it, by package
```

`--probes` has its own ceilings, `PROBE_CEILINGS`, under the same rule as the
site's: about 3% over what was measured, raised only by a dated note that says
why. CI runs both in *Build the site*.

`probe-attribution.py` reads the binary from *before* Binaryen optimised it,
because that one still has its function names. Its byte counts are therefore
larger than the shipped ones; they are for comparing packages with each other,
not for adding up to the figure above.

## Why there is nothing like R8 to turn on

There is no need for one. A production Kotlin/Wasm or Kotlin/JS executable is
compiled as a whole program: the compiler starts from `main` and keeps only what
it can reach, then Binaryen optimises what is left. Kotlin/Native links an iOS
app the same way. An unused component, or an unused icon, is never in the
binary to begin with. The probes are how that is checked rather than assumed.

What it cannot strip is whatever the reachable code itself refers to. Anything
`KontourTheme` builds by default is reachable from every app that calls it, and
that is what `theme` measures.

The JVM is the exception. A desktop app ships every class of every dependency
unless it is shrunk, which Compose Desktop does with ProGuard; see
[Installing](../ui-docs/content/installing.md#the-size-of-an-app).
