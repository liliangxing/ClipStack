# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/23.0.2/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Shizuku SDK and user service classes must not be obfuscated or renamed.
# The AIDL-generated stubs and the service implementation are referenced by
# name via reflection by the Shizuku framework.
-keep class rikka.shizuku.** { *; }
-keep class com.catchingnow.clip.IClipboardService { *; }
-keep class com.catchingnow.clip.IClipboardService$Stub { *; }
-keep class com.catchingnow.clip.IClipboardService$Stub$Proxy { *; }
-keep class com.catchingnow.clip.IClipboardCallback { *; }
-keep class com.catchingnow.clip.IClipboardCallback$Stub { *; }
-keep class com.catchingnow.clip.IClipboardCallback$Stub$Proxy { *; }
-keep class com.catchingnow.clip.ShizukuClipboardService { *; }
-keep class com.catchingnow.clip.ShizukuClipboardService$* { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
