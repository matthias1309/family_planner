# kotlinx.serialization: generierte Modelle und Routen
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class de.familyplanner.api.model.**$$serializer { *; }
-keepclassmembers class de.familyplanner.api.model.** {
    *** Companion;
}
-keepclasseswithmembers class de.familyplanner.api.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Retrofit-Interfaces aus dem OpenAPI-Client
-keep interface de.familyplanner.api.client.** { *; }
# Navigation-Routen (typsicher, serialisiert)
-keep,includedescriptorclasses class de.familyplanner.android.feature.auth.*Route$$serializer { *; }
-keepclassmembers class de.familyplanner.android.feature.auth.*Route {
    *** Companion;
}
