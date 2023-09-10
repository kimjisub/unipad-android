# Begin: Common Proguard rules

# Don't note duplicate definition (Legacy Apche Http Client)
-dontnote android.net.http.*
-dontnote org.apache.http.**

# Add when compile with JDK 1.7
-keepattributes EnclosingMethod

# GSON
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

-keep class com.kimjisub.launchpad.activity.MainActivity { *; }


# End: Common Proguard rules