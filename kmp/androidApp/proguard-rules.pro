# R8 rules for the app itself; libraries (Room, kotlinx.serialization, PostHog, SQLite,
# the image cropper) ship their own.

# JNA and the UniFFI bindings reach the Rust core by reflection (structures, callbacks).
-dontwarn java.awt.*
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep class com.backtosafety.core.ffi.** { *; }

# Stack traces in crash reports keep file and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
