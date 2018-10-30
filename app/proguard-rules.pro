# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#-keepattributes Signature
#-keepclassmembers class com.kimjisub.launchpad.fb.** {
#  *;
#}
#-keepclassmembers class com.kimjisub.launchpad.manage.network.** {
#   *;
# }
#-keep class android.support.v8.renderscript.** { *; }
#-keep class com.android.vending.billing.**

# vungle
#-dontwarn com.vungle.**
#-dontnote com.vungle.**
#-keep class com.vungle.** { *; }
#-keep class javax.inject.*
## ignore eventbus warnings
#-dontwarn de.greenrobot.event.util.**
## ignore rx warnings
#-dontwarn rx.internal.util.unsafe.**
## keep some important rx stuff - https://github.com/ReactiveX/RxJava/issues/3097
#-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
#    long producerIndex;
#    long consumerIndex;
#}
#-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
#    rx.internal.util.atomic.LinkedQueueNode producerNode;
#}
#-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
#    rx.internal.util.atomic.LinkedQueueNode consumerNode;
#}
#-keep class rx.schedulers.Schedulers { public static <methods>; }
#-keep class rx.schedulers.ImmediateScheduler { public <methods>; }
#-keep class rx.schedulers.TestScheduler { public <methods>; }
#-keep class rx.schedulers.Schedulers { public static ** test(); }