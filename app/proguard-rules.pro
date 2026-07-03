# ══════════════════════════════════════════════════════════════════════════
# R8/ProGuard — règles de conservation (release, R8 activé depuis v0.32.0)
#
# Posture volontairement CONSERVATRICE : on garde plus que nécessaire plutôt
# que de risquer un crash runtime (sérialisation, réflexion Room/Hilt/Firebase)
# qu'aucun test JVM/instrumenté ne peut détecter ici — seul un test manuel sur
# l'APK release signé le peut. Ne pas retirer une règle sans avoir vérifié le
# scénario correspondant sur device.
# ══════════════════════════════════════════════════════════════════════════

-dontwarn org.jetbrains.annotations.**

# ─── Room (entities, DAOs, base) ───────────────────────────────────────────
# Room génère les implémentations DAO/Database par annotation processing (kapt) et les
# référence directement (pas de réflexion pure), mais on garde explicitement les entities/DAOs
# pour éviter que R8 ne renomme/supprime des champs lus par les requêtes SQL générées.
-keep class com.rebuildit.prestaflow.data.local.db.** { *; }
-keep class com.rebuildit.prestaflow.data.local.entity.** { *; }
-keep interface com.rebuildit.prestaflow.data.local.dao.** { *; }
-keep class com.rebuildit.prestaflow.data.local.dao.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ─── kotlinx-serialization ──────────────────────────────────────────────────
# Garde les sérialiseurs générés par le plugin kotlinx.serialization (companion $$serializer)
# et les classes @Serializable elles-mêmes (champs lus/écrits par le sérialiseur généré, hors
# du graphe d'appel direct que R8 peut suivre).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rebuildit.prestaflow.**$$serializer { *; }
-keepclassmembers class com.rebuildit.prestaflow.** {
    *** Companion;
}
-keepclasseswithmembers class com.rebuildit.prestaflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowshrinking,includedescriptorclasses class com.rebuildit.prestaflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.rebuildit.prestaflow.** { *; }

# DTOs réseau et modèles domaine/entities : sérialisés (JSON) et/ou lus par Room, jamais
# minifiables sans casser le parsing des réponses API ou les colonnes SQLite.
-keep class com.rebuildit.prestaflow.data.remote.dto.** { *; }
-keep class com.rebuildit.prestaflow.domain.**.model.** { *; }

# ─── Retrofit ────────────────────────────────────────────────────────────
# Retrofit/OkHttp fournissent déjà leurs consumer-rules (embarquées dans les AAR) ; on garde
# ici uniquement l'interface API du module (signatures génériques Call/suspend + annotations).
-keep interface com.rebuildit.prestaflow.data.remote.api.** { *; }
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ─── Hilt / Dagger ───────────────────────────────────────────────────────
# Hilt fournit ses propres consumer-rules ; on garde par prudence les classes générées et les
# points d'entrée annotés (@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel).
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class **_HiltModules { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# ─── Firebase Cloud Messaging ────────────────────────────────────────────
-keep class com.google.firebase.messaging.** { *; }
-keep class com.rebuildit.prestaflow.data.push.PrestaFlowFirebaseMessagingService { *; }
-keep class com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager { *; }
-keep class com.rebuildit.prestaflow.core.notifications.** { *; }

# ─── Timber ──────────────────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }

# ─── Impression thermique (ESC/POS Bluetooth, DantSu) et scan code-barres (ZXing) ─────────
# Libs tierces sans garantie explicite de consumer-rules complètes pour R8 : conservateur.
-keep class com.dantsu.escposprinter.** { *; }
-dontwarn com.dantsu.escposprinter.**
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
