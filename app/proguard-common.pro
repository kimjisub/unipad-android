# Begin: Common Proguard rules

# Don't note duplicate definition (Legacy Apche Http Client)
-dontnote android.net.http.*
-dontnote org.apache.http.**

# Add when compile with JDK 1.7
-keepattributes EnclosingMethod

# GSON
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
# Gson models without @SerializedName: R8 renamed the fields, so every unipad:// import got a
# UnishareVO with null fields and downloaded .../unishare/null/download in release builds.
-keep class com.kimjisub.launchpad.api.**.vo.** { *; }

-keep class com.kimjisub.launchpad.activity.MainActivity { *; }

# Keep MIDI driver classes instantiated via reflection in MidiSelectActivity
-keep class com.kimjisub.launchpad.midi.driver.LaunchpadS { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.LaunchpadMK2 { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.LaunchpadPRO { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.LaunchpadX { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.LaunchpadMK3 { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.Matrix { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.MidiFighter { <init>(); }
-keep class com.kimjisub.launchpad.midi.driver.MasterKeyboard { <init>(); }

# End: Common Proguard rules