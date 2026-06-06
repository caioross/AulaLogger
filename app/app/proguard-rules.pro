# Strip de Log.v/d/i em release — Log.w e Log.e permanecem para crash reports.
# SEC-004: evita vazamento de paths, transcripts e estados internos via adb logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# AulaLoggerLog wrapper similar — caso volte a usar:
-assumenosideeffects class com.aulalogger.**.AulaLoggerLog {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Whisper JNI — manter classes nativas e métodos external
-keep class com.aulalogger.transcription.WhisperJNI { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.aulalogger.transcription.WhisperJNI$Segment { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.aulalogger.**$$serializer { *; }
-keepclassmembers class com.aulalogger.** {
    *** Companion;
}
-keepclasseswithmembers class com.aulalogger.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose Stable & MutableStateFlow
-dontwarn androidx.compose.**

# Mantém entry points
-keep class com.aulalogger.MainActivity { *; }
-keep class com.aulalogger.AulaLoggerApp { *; }
-keep class com.aulalogger.recording.RecordingService { *; }
-keep class com.aulalogger.transcription.TranscriptionService { *; }
-keep class com.aulalogger.widget.AulaLoggerWidget { *; }

# Data classes serializadas
-keep class com.aulalogger.data.Session { *; }

# Suppress warnings comuns
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# Tink (dependência transitiva de androidx.security:security-crypto) referencia
# anotações errorprone que NÃO existem em runtime — apenas em build time. R8
# reclama se não dissermos para ignorar.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# Manter atributos úteis para crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
