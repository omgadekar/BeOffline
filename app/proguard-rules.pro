# BeOffline R8 rules.
#
# Keep this file narrow. Every `-keep ... { *; }` that is not strictly needed
# drags down the DEX obfuscation percentage Play measures on the release
# dashboard, which has to stay above 25%.
#
# Libraries that ship their own consumer rules are deliberately NOT repeated
# here: Hilt/Dagger, Room, Retrofit, OkHttp, WorkManager and Firebase all carry
# rules inside their artifacts, and duplicating them by hand is how a stale
# `-keep` survives a library upgrade.
#
# Gson is the exception, and it cost a crash to find out. Gson only began
# embedding META-INF/proguard rules in 2.11.0; the 2.10.1 jar on this
# classpath has none at all, so nothing kept TypeToken and R8 renamed it.
#
# Components declared in AndroidManifest.xml -- the VPN service, the open-block
# accessibility service, the FCM service, the receivers -- are kept by the
# rules AGP generates from the manifest. They do not need entries here.

# -- Gson wire types ---------------------------------------------------------
# Nothing in the accountability package uses @SerializedName: the Kotlin
# property name IS the JSON key. Those names are not only the live API
# contract, they are also written to disk -- outbox rows and the group cache
# store serialized JSON in Room -- so a renamed field breaks both the server
# round-trip and data already written by an installed build.
#
# -keepclassmembers is NOT enough here. It means "if this class is kept, keep
# these members", and nothing else keeps these classes, so under R8 full mode
# the rule never fires: a build with it renamed requesterUid, resolvedByUid,
# lastSeenAtUtc, alreadyReported and grantedDurationMinutes out of the DEX.
# The classes themselves have to be kept for the member rule to bind.
-keep class com.beoffline.app.accountability.*Dto { <fields>; }
-keep class com.beoffline.app.accountability.*Body { <fields>; }
-keep class com.beoffline.app.accountability.ChatOutboxPayload { <fields>; }

# Converters resolves List<String> / List<Int> through anonymous TypeToken
# subclasses, which are useless without their generic signature.
#
# Keeping the subclasses is not enough on its own. TypeToken reads its own
# type argument back out of the subclass's generic supertype, so the base
# class has to survive intact too — with 2.10.1 bringing no rules of its own,
# R8 renamed it to t4.a, the signature went with it, and the first Room read
# after launch threw "TypeToken must be created with a type argument". That is
# every rule the app has ever stored, on the first screen, in release only.
#
# These three lines are Gson's own rules, the ones 2.11.0 started shipping.
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# -- Room --------------------------------------------------------------------
# Entities and DAOs are reached by generated code rather than reflection, so
# they need no keep. The converter class is named by the generated database.
-keep class com.beoffline.app.data.local.Converters { *; }

# -- WorkManager -------------------------------------------------------------
# WorkManager persists each worker's class NAME in its own database. A name
# that changes between releases leaves work enqueued by the old build
# unrunnable after the update, which would silently break scheduled blocks.
-keep class * extends androidx.work.ListenableWorker

# -- SignalR -----------------------------------------------------------------
# Plain JAR, so it ships no consumer rules, and it reflects over hub handler
# argument types. Its optional logging backends are not on the classpath.
-keep class com.microsoft.signalr.** { *; }
-dontwarn com.microsoft.signalr.**
-dontwarn org.slf4j.**
