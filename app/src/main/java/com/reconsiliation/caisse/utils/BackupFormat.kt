package com.reconsiliation.caisse.utils

import java.io.File

/**
 * Format d'un fichier de sauvegarde.
 *
 * Deux formats coexistent par nécessité (B-144) :
 * - [Encrypted] : archive ZIP chiffrée par mot de passe utilisateur, produite
 *   par les parcours disposant d'une interface (Paramètres, Clôture) ;
 * - [LegacyRaw] : copie brute du fichier SQLCipher, produite par les traitements
 *   sans interface — `BackupWorker` (quotidien) et le miroir externe — où aucun
 *   mot de passe ne peut être saisi.
 *
 * La détection repose sur la **signature binaire** du fichier et non sur son
 * extension : un utilisateur peut renommer un fichier, et confondre les deux
 * formats corromprait la base (risque R1 de l'analyse d'impact).
 */
sealed interface BackupFormat {

    /** Archive `BackupManager` (ZIP chiffré, mot de passe requis). */
    data object Encrypted : BackupFormat

    /** Base SQLCipher brute : restaurable sans mot de passe. */
    data object LegacyRaw : BackupFormat

    /** Ni l'un ni l'autre : fichier corrompu ou sans rapport. */
    data class Unknown(val reason: String) : BackupFormat

    companion object {
        /** `PK\x03\x04` — en-tête d'une archive ZIP (RFC 1952 / APPNOTE). */
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        /**
         * Un fichier SQLCipher est **entièrement chiffré**, en-tête compris :
         * contrairement à SQLite, il ne commence pas par « SQLite format 3 ».
         * On ne peut donc que vérifier des propriétés structurelles.
         *
         * Taille de page minimale de SQLite : 512 octets. Un fichier plus petit
         * ne peut pas être une base valide.
         */
        private const val MIN_DB_SIZE = 512L

        /**
         * Détermine le format de [file] à partir de son contenu.
         *
         * Ne lève jamais d'exception : un fichier illisible retourne [Unknown].
         */
        fun detect(file: File): BackupFormat {
            if (!file.exists()) return Unknown("Fichier introuvable")
            if (file.length() == 0L) return Unknown("Fichier vide")

            val header = ByteArray(4)
            val read = try {
                file.inputStream().use { it.read(header) }
            } catch (e: Exception) {
                return Unknown("Fichier illisible : ${e.message}")
            }
            if (read < 4) return Unknown("Fichier trop court (${file.length()} octets)")

            if (header.contentEquals(ZIP_MAGIC)) return Encrypted

            // Ni ZIP, ni assez volumineux pour être une base.
            if (file.length() < MIN_DB_SIZE) {
                return Unknown("Trop petit pour une base de données (${file.length()} octets)")
            }

            // Une base SQLCipher est indiscernable d'un flux aléatoire : par
            // élimination, tout fichier non-ZIP de taille plausible est traité
            // comme une base brute. La tentative d'ouverture tranchera.
            return LegacyRaw
        }

        /** Extension recommandée pour un export dans le format donné. */
        fun extensionFor(format: BackupFormat): String = when (format) {
            Encrypted -> "zip"
            LegacyRaw -> "db"
            is Unknown -> "bin"
        }
    }
}
