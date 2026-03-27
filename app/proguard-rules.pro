# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to the build default flags.

# Keep Hilt
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.beoffline.app.data.model.** { *; }

# Keep Gson for TypeConverters
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep VpnService
-keep class com.beoffline.app.vpn.** { *; }

# Keep WorkManager workers
-keep class com.beoffline.app.scheduler.** { *; }
