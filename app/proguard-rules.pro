# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit classes and annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Keep Moshi / JSON Serialization
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep Room Database Components
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Keep Cryptography and AndroidX Security
-dontwarn androidx.security.crypto.**
-keep class androidx.security.crypto.** { *; }

# Keep App Data Transfer Objects & Entities to prevent reflection issues during JSON serialization / DB mapping
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.security.** { *; }
