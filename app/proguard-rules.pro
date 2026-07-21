# Retrofit / OkHttp / Moshi keep rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*

# Moshi reflective adapters keep model classes
-keep class com.memoria.mobile.data.remote.** { *; }
-keepclassmembers class com.memoria.mobile.data.remote.** { *; }
