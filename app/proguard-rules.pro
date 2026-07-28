# Règles R8 — MobileCaisse
# Référencé par app/build.gradle.kts. isMinifyEnabled est encore false (B-022) :
# ces règles sont posées à l'avance pour que l'activation de R8 ne casse rien.

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- SQLCipher (JNI : la réflexion ne voit pas ces classes) ---
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# --- kotlinx.serialization (BackupManager : BackupMetadata, BackupManifest) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- ML Kit / Play Services (scan de code-barres) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.android.gms.**

# --- Entités et modèles du projet (sérialisés ou passés par Intent) ---
-keep class com.reconsiliation.caisse.data.local.entity.** { *; }
-keep class com.reconsiliation.caisse.utils.BackupMetadata { *; }
-keep class com.reconsiliation.caisse.utils.BackupManifest { *; }

# --- Conserver les numéros de ligne pour des traces exploitables ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
