# ---- SafeGuard release (R8) rules ----
# Readable crash stack traces
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# App data/model classes (Room entities, telemetry models) - tiny, keep them intact to be safe
-keep class com.example.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# MPAndroidChart (custom MarkerView / listeners are used by the Live Chart screen)
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Optional networking classes referenced by OkHttp / Retrofit (not present on Android)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn retrofit2.**

# Firebase / Play services / Compose / coroutines ship their own consumer rules.
