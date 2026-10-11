# R8 rules for the app itself; libraries (Room, kotlinx.serialization, PostHog, SQLite,
# the image cropper) ship their own.

# JNA and the UniFFI bindings reach the Rust core by reflection (structures, callbacks).
-dontwarn java.awt.*
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
# Only what JNA finds by name stays: the native functions, structures, callbacks. The rest of
# the generated bindings (converters, records, enums) is shrunk like any other code.
-keepclasseswithmembernames class com.backtosafety.core.ffi.** { native <methods>; }
-keep class * extends com.sun.jna.Structure { *; }
-keep interface * extends com.sun.jna.Callback { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep interface * extends com.sun.jna.Library { *; }

# Stack traces in crash reports keep file and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
