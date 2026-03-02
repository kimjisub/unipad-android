# Begin: Proguard rules for okhttp3 (4.12.x)

# OkHttp 4.x ships consumer ProGuard rules in its AAR.
# These rules supplement them for platform-specific classes.

-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# End: Proguard rules for okhttp3