# QuickJS native bridge; keep JNI surface and callback names.
-keep class com.dokar.quickjs.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Plugin host JSON contract is accessed by name from JS/JSON.
-keep class com.inkreader.plugin.quickjs.PluginManifest { *; }
-keep class com.inkreader.plugin.quickjs.PluginSnapshot { *; }

# Kotlin/Compose metadata is handled by AGP; keep annotations for reflection-free paths.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
