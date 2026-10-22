# Keep UVC USB stack classes referenced only from native/JNI paths.
-keep class com.serenegiant.** { *; }
-keep class com.jiangdg.** { *; }
-dontwarn com.serenegiant.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.irlstream.** {
    *** Companion;
}
-keepclasseswithmembers class com.irlstream.** {
    kotlinx.serialization.KSerializer serializer(...);
}
