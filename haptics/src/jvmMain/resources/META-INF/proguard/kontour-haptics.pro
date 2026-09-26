# The trackpad's class is made by name, only on a Mac with Java 22, so nothing
# refers to it for a shrinker to follow. Keep it and its constructor.
-keep class io.kontour.haptics.MacTrackpadFfm { <init>(); }

# It is written against Java 22's foreign-function API; on an older JDK the
# shrinker sees references it cannot resolve, and they are never reached there.
-dontwarn java.lang.foreign.**
