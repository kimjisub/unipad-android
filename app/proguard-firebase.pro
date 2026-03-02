# Begin: Proguard rules for Firebase (BOM 33.x)

# Firebase SDK bundles its own consumer ProGuard rules.
# These rules cover app-specific Firebase data model classes.

-keepattributes *Annotation*
-keepattributes Signature

# Keep Firebase data model classes used with Firestore/Realtime Database
-keepclassmembers class com.kimjisub.launchpad.network.fb.** {
  *;
}

# End: Proguard rules for Firebase