# ProGuard / R8 rules for TalkLoop V3

# Google Tink Cryptographic Library
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Kopus / Opus Native JNI (artifact coordinates: eu.buney.kopus:kopus)
-keep class eu.buney.kopus.** { *; }
-dontwarn eu.buney.kopus.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Play Services Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ZXing QR Code Engine
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# AndroidX DataStore Preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# App Protocol and Models
-keep class com.example.offlinewalkietalkie.protocol.** { *; }
-keep class com.example.offlinewalkietalkie.channel.** { *; }
-keep class com.example.offlinewalkietalkie.diagnostics.** { *; }
-keep class com.example.offlinewalkietalkie.voice.** { *; }
-keep class com.example.offlinewalkietalkie.data.OfflineMap** { *; }
