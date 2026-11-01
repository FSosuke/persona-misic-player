# NewPipeExtractor uses reflection (Rhino JS engine for signature deciphering, jsoup, nanojson).
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**

# jaudiotagger (tagging downloads) instantiates frame classes by name and references desktop-only APIs.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.sound.**

# Kuromoji (romaji lyrics) loads its dictionary resources relative to its own classes.
-keep class com.atilika.kuromoji.** { *; }

# Anthropic Java SDK ("Igor speaks"): Jackson-based (de)serialization of the API models.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
