package io.kontour.haptics

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * A Mac's trackpad, through AppKit's `NSHapticFeedbackManager`, called with
 * Java's foreign-function API — no JNI library to ship, and nothing loaded on a
 * JVM without it: this is the one class that names `java.lang.foreign`, and
 * [DesktopHaptics] reaches it only by name, on Java 22 or later.
 *
 * Three calls into the Objective-C runtime make the performer — look the class
 * up, name the selector, send the message — and one message plays a pattern:
 * `[NSHapticFeedbackManager.defaultPerformer performFeedbackPattern:performanceTime:]`,
 * with "now" as the time.
 */
@Suppress("unused") // Made by name, in `loadTrackpad`.
internal class MacTrackpadFfm : TrackpadActuator {

    private val performer: MemorySegment
    private val perform: MemorySegment
    private val send: MethodHandle

    init {
        val linker = Linker.nativeLinker()
        val arena = Arena.global()
        // AppKit loaded first, so the class below exists to be looked up; both
        // live in the dyld shared cache, which dlopen reads by these paths.
        SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", arena)
        val objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", arena)
        val getClass = linker.downcallHandle(
            objc.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
        val selector = linker.downcallHandle(
            objc.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
        val messageSend = objc.find("objc_msgSend").orElseThrow()
        val sendForObject = linker.downcallHandle(
            messageSend,
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
        send = linker.downcallHandle(
            messageSend,
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
        )
        Arena.ofConfined().use { names ->
            val manager = getClass.invokeWithArguments(names.allocateFrom("NSHapticFeedbackManager")) as MemorySegment
            require(manager != MemorySegment.NULL) { "NSHapticFeedbackManager is not there" }
            val defaultPerformer = selector.invokeWithArguments(names.allocateFrom("defaultPerformer")) as MemorySegment
            performer = sendForObject.invokeWithArguments(manager, defaultPerformer) as MemorySegment
            require(performer != MemorySegment.NULL) { "No default haptic performer" }
            perform = selector.invokeWithArguments(names.allocateFrom("performFeedbackPattern:performanceTime:")) as MemorySegment
        }
    }

    override fun perform(pattern: MacPattern) {
        send.invokeWithArguments(performer, perform, pattern.code, PerformanceTimeNow)
    }

    private companion object {
        /** `NSHapticFeedbackPerformanceTimeNow`. */
        const val PerformanceTimeNow = 1L
    }
}
