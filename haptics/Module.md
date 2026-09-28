# Module kontour-haptics

Platform haptics for Kotlin Multiplatform, with no Compose dependency.

One vocabulary of effects — `HapticEffect` — played through each platform's own
tuned feedback first: Android's composition primitives and feedback constants,
UIKit's feedback generators, Core Haptics for what UIKit cannot say, the
browser's Vibration API, and a Mac's Force Touch trackpad. `Haptics` plays them;
`RecordingHaptics` writes them down for a test.

Kontour UI depends on this and decides which interaction gets which effect.
This module decides only how a platform plays one.

# Package io.kontour.haptics

The effects, the player, and its per-platform factories.
