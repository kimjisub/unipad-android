# Begin: Proguard rules for Firebase

# Authentication
-keepattributes *Annotation*

# Realtime database
-keepattributes Signature

-keepattributes Signature
-keepclassmembers class com.kimjisub.launchpad.network.fb.* {
  *;
}

# End: Proguard rules for Firebase