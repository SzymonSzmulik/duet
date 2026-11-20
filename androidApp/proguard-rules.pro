# NewPipe Extractor and its Rhino JavaScript engine load classes by reflection.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn org.slf4j.**
# Methods the web pages call (DuetBridge).
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
# Ktor / coroutines
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**
