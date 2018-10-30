# Begin: Proguard rules for retrofit2

# Vungle
-keep class com.vungle.warren.** { *; }
# Evernote
-dontwarn com.evernote.android.job.gcm.**
-dontwarn com.evernote.android.job.GcmAvailableHelper
-dontwarn com.google.android.gms.ads.identifier.**
-keep public class com.evernote.android.job.v21.PlatformJobService
-keep public class com.evernote.android.job.v14.PlatformAlarmService
-keep public class com.evernote.android.job.v14.PlatformAlarmReceiver
-keep public class com.evernote.android.job.JobBootReceiver
-keep public class com.evernote.android.job.JobRescheduleService
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep class com.google.android.gms.internal.** { *; }
# Moat SDK
-keep class com.moat.** { *; }
-dontwarn com.moat.**

-dontwarn com.vungle.warren.error.VungleError$ErrorCode

# End: Proguard rules for retrofit2