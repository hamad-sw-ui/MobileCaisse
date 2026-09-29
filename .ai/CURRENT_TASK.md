# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire post-J6.7b LOT 2. Aucune implémentation en cours. J6.7b LOT 2 est clôturé techniquement (`11d47cb`). **J6.7b LOT 3 n'est pas commencé.**

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `11d47cb908905249f31b5761b115f66033919764` |
| Commit | `feat(j6.7): implement remote backup repository and worker` |
| Parent | `2ade92ad026d49c5708c394689ca23dfa605151f` |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| J6.5 | TERMINÉ TECHNIQUEMENT (`498b9ba`) |
| J6.6 | TERMINÉ TECHNIQUEMENT (`a34e5f3`) + Sync `.ai/` (`c00deba`) |
| J6.7a / B-111 | CONCEPTION TERMINÉE (`.ai/REPORTS/analyse_conception_2026-09-27_sauvegarde_distante_google_drive.md`) |
| J6.7b LOT 1 / B-112 | TERMINÉ TECHNIQUEMENT (`8bac07c`) + Sync `.ai/` (`2ade92a`) |
| J6.7b LOT 2 / B-112 | **TERMINÉ TECHNIQUEMENT** (`11d47cb`) — post-commit **VALIDÉ** |
| J6.7b LOT 3 | **NEXT / NOT STARTED** (attente d'autorisation d'exécution) |

## J6.7b LOT 2 — RemoteBackupRepository, Worker & Rotation (terminé)

Livré dans le commit `11d47cb` (6 fichiers techniques, +693 / -0, aucun `.ai/`) :
- **`RemoteBackupRepository`** (`RemoteBackupRepository.kt`) : orchestre l'export localement chiffré v2 (`BackupManager.exportBackupWithPassword()`) vers un temporaire `.db.enc` privé (supprimé dans `finally`), l'upload cloud, la rotation et la traçabilité `"REMOTE_BACKUP"` (`INFO`/`CRITICAL`) via `MainRepository.logAction()`.
- **Dérivation déterministe de secret** : `deriveRemoteBackupPassword(managerCode: String)` applique le suffixe `"a1A!"` à `managerCode.trim()` pour garantir la présence simultanée de minuscules, majuscules et chiffres. Secret 100% reproductible pour la future restauration (LOT 3).
- **Politique de rotation** : déduplique les sauvegardes multiples du même jour UTC, conserve les 7 jours civils distincts les plus récents et le 1er fichier de jusqu'à 4 mois civils antérieurs. Erreurs de suppression individuelles non bloquantes.
- **`RemoteBackupWorker`** (`RemoteBackupWorker.kt`) : `@HiltWorker` + `@AssistedInject` planifié via `schedulePeriodic()` (24h, `ExistingPeriodicWorkPolicy.KEEP`, backoff exponentiel 15 min, contraintes `UNMETERED`/`CONNECTED` + batterie non faible). *(Note : helper de planification prêt, branchement UI effectif reporté au LOT 3)*.
- **Extensions `PreferencesManager`** : `remote_backup_enabled`, `wifi_only_backup_enabled`, `last_remote_backup_time`, `last_remote_backup_status`.
- **Hilt DI** (`RemoteStorageModule.kt`) : bindings `@Singleton` pour `RemoteBackupRepository` et `PreferencesManager`.
- **Tests** : 8 tests JVM unitaires dans `RemoteBackupRepositoryTest` avec `FakeRemoteStorageClient` et `TestContext`.
- 185/185 tests JVM ; `compileDebugKotlin` / KSP / **`assembleDebug` OK** ; APK debug généré.

Phase actuelle = **synchronisation documentaire AI-DOS J6.7b LOT 2**.

## Encore ouvert dans le jalon 6

Sauvegarde distante :
- J6.7b LOT 3 (B-112-LOT3) : UI `SettingsScreen` / `BackupScreen`, i18n, restauration cloud. **NEXT / NOT STARTED**.
Type monétaire (6.8).

---
*Mis à jour le 2026-09-28 (clôture J6.7b LOT 2, commit 11d47cb). Précédente fiche : 2026-09-28, J6.7b LOT 1.*
