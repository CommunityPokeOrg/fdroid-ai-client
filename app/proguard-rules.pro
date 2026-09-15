# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class org.communitypoke.fdroidai.data.model.** {
    <init>(...);
    <fields>;
}
-keep,includedescriptorclasses class org.communitypoke.fdroidai.**$$serializer { *; }
-keepclasseswithmembers class org.communitypoke.fdroidai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
