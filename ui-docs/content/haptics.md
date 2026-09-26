# Haptics

The library's haptics are played by a module of their own, `io.kontour:haptics`.
It has no Compose in it — it plays effects, and nothing about that needs a UI
toolkit — so it comes with `:ui` and can be used without it:

```kotlin
dependencies {
    implementation("io.kontour:haptics:<version>")
}
```

`:ui` decides **which** effect each interaction gets — the intents, the levels,
the rate limit; see [the theming guide](theming.md#physical-feedback). This
module decides only **how a platform plays one**, and it reaches for the
platform's own tuned feedback first: what a phone's maker tuned for its own
actuator, and what Apple tuned for the Taptic Engine.

---

## Playing an effect

```kotlin
val haptics = Haptics(context, view)      // Android
val haptics = Haptics()                   // iOS, the desktop, the web

haptics.play(HapticEffect.Selection(fine = true))
haptics.play(HapticEffect.Impact(ImpactStyle.Soft, strength = 0.6f))
haptics.play(HapticEffect.Notification(NotificationType.Success))
```

Inside a Kontour app, `rememberHaptics()` is the player the theme plays through.

**Call it from the main thread.** Nothing waits, and **nothing limits the rate**:
a haptic a frame is a buzz on every platform, because each new one cuts off the
last, and deciding which of a stream to drop is a question of what the stream
is. `:ui`'s detents keep reports 80ms apart.

**`effect.scaled(factor)`** is the same effect at that share of its strength —
a texture that follows a finger's speed is one effect played at a strength of
the moment:

```kotlin
haptics.play(HapticEffect.LowTick(0.6f).scaled(speed))
```

**`strength` is 0 to 1**, clamped when played: NaN and anything below 0 is 0,
anything above 1 is 1, and 0.001 or less plays nothing. At 1 a system effect is
the platform's own version; below it, softer, as far as the platform can say —
`capability.honoursStrength` says how far that is.

## The effects

Two families.

**Primitives** — `Tick`, `LowTick`, `Click`, `Thud`, `Spin`, `QuickRise`,
`SlowRise`, `QuickFall` — are Android's composition primitives by name, and the
only effects a pattern can hold.

**System effects** say what happened and let the platform play its own:

| Effect | Android, with primitives | Android, without | iOS | Web |
|---|---|---|---|---|
| `Selection(fine)` | `TICK` at a half, or 0.8 for a coarse step | `SEGMENT_FREQUENT_TICK` / `SEGMENT_TICK` from Android 14, the clock and context-click ticks before | the selection generator | 12–20ms |
| `Impact(style)` | `CLICK`, `THUD`, `LOW_TICK` or `TICK` by style | `VIRTUAL_KEY`, `LONG_PRESS` or the ticks by style | the impact generator, that style, at that strength | 10–36ms by style |
| `Notification(type)` | a rising pair; two `CLICK`s 140ms apart, the second a step lighter; or three even knocks | `CONFIRM` / `LONG_PRESS` then `VIRTUAL_KEY` 140ms later / `REJECT` | the notification generator | a short rhythm |
| `Toggle(on)` | `TICK` at a half on, `LOW_TICK` at 0.3 off | `TOGGLE_ON` / `TOGGLE_OFF` from Android 14 | a light impact on, a soft one off | 16–22ms / 10–14ms |
| `Threshold(activate)` | `CLICK` in, a light `TICK` back | `GESTURE_THRESHOLD_ACTIVATE` / `…_DEACTIVATE` from Android 14 | a rigid impact in, a soft one back | 18–24ms / 10–14ms |
| `LongPress`, `KeyPress` | `CLICK` | `LONG_PRESS`, `KEYBOARD_TAP` | a medium, a light impact | 26–32ms, 10–14ms |

A Mac's trackpad has three patterns — generic, alignment, level change — and
plays each effect as the nearest of them. Every strength here is a starting
point, set by feel: the catalog's **Haptics** page plays each one on a device.

## Patterns and rumbles

A **pattern** is primitives on a timeline, played as *one* native pattern where
the platform has such a thing — an Android composition, a Core Haptics pattern, a
single vibrate array — so its timing is the platform's rather than a timer's:

```kotlin
haptics.play(
    hapticPattern {
        at(0.milliseconds, HapticEffect.Tick(0.5f))
        after(60.milliseconds, HapticEffect.Click(0.9f))
    },
)
```

A **rumble** is continuous, steered while it plays, and stopped:

```kotlin
val rumble = haptics.startRumble(intensity = 0.15f, sharpness = 0.2f)
rumble.update(intensity = 0.45f, sharpness = 0.2f)
rumble.stop()
```

It is a lease. Every `update` renews it, and one that runs out — ten seconds
unless told otherwise — stops the rumble, so a caller that forgets it leaves a
phone buzzing for that long at most. Sharpness is how buzzy rather than dull it
is, where a platform can say so: Core Haptics, and Android's envelopes from
Android 16. Elsewhere it is a vibration held at a strength, or a rapid run of the
lightest tick.

## Per platform

**Android** plays through the richest route the phone allows.

- **Composition primitives**, from Android 11 on an actuator that reports them,
  at any strength. They are played as *touch feedback*, so the system's
  touch-vibration setting and intensity reach them; before Android 13, which has
  no way to say so, the module reads the setting itself.
- **The system's feedback constants**, through the `View` passed in, on a phone
  without primitives. They need no permission and are what such a phone is tuned
  for.
- **Predefined effects**, with no view.

The primitives need `android.permission.VIBRATE`, which the module's manifest
declares — a normal permission, granted at install and never prompted for. An
app that removes it (`tools:node="remove"`) gets the constants; a phone that
refuses a vibration for any other reason moves that player to them for good.

**iOS** plays one-offs through UIKit's feedback generators — Apple's own, which
follow the system's haptics switch — and uses Core Haptics only for the swells,
patterns and rumbles UIKit has no word for. There is one engine for the
process, started when first needed and again whenever the system stopped it,
including after the app has been in the background. An iPad has no Taptic
Engine and plays nothing. A static framework may need `-framework CoreHaptics`
in the app's linker flags.

**The web** has the Vibration API where there is one: Chrome and Firefox on
Android. Safari has none on any device. It has no strength, only length, so a
softer effect is a shorter one; a browser ignores it until the reader has
touched the page; and a desktop browser may report it and have no motor.

**The desktop** has a Mac's Force Touch trackpad, reached through Java's
foreign-function API on Java 22 or later, and nothing on Windows or Linux. The
trackpad plays only while a finger is on it. Run the app with
`--enable-native-access=ALL-UNNAMED` to keep Java from warning about the native
call, and keep `io.kontour.haptics.MacTrackpadFfm` in a minified build — the jar
ships the ProGuard rule for it.

## What a device can do

`haptics.capability` says how rich its haptics are (`None`, `Basic`, `Rich`),
whether strength is felt finely, what a rumble is (`None`, `Pulsed`,
`Continuous`), and — in `details` — which route was taken and why, for a person
reading it.

## Testing

`RecordingHaptics` plays nothing and writes down what it was asked, in order.
Provide it as `LocalHaptics` above a `KontourTheme` to read back what a gesture
played rather than which intent it asked for:

```kotlin
val player = RecordingHaptics()
CompositionLocalProvider(LocalHaptics provides player) {
    KontourTheme { Screen() }
}
// …
assertEquals(listOf(HapticRecord.Played(FeedbackIntent.ToggleOn.defaultEffect)), player.records)
```

`Haptics.None` plays nothing and never fails, for screenshots and previews.
