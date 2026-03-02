# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Coroutine
-keepclassmembernames class kotlinx.* {
    volatile <fields>;
}

# ViewModels — keep constructors for Factory instantiation
-keep class com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel { <init>(...); }
-keep class com.kimjisub.launchpad.viewmodel.MainPackPanelViewModel { <init>(...); }
