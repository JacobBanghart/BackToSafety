# R8 rules for the app itself; libraries (Room, kotlinx.serialization, PostHog, SQLite,
# the image cropper) ship their own.

# Stack traces in crash reports keep file and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
