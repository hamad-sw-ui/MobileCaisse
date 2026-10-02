# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire post-J6.7b LOT 3A. Aucune implémentation en cours. J6.7b LOT 3A est clôturé techniquement (`55067de`). **J6.7b LOT 3B n'est pas commencé.**

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `55067deb3ae18e7b470a10bec85d0415b89806d2` |
| Commit | `feat(j6.7): implement remote backup ViewModel and startup wiring` |
| Parent | `a0272455e0236f46a48d2120066e1e19759f2240` |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| J6.5 | TERMINÉ TECHNIQUEMENT (`498b9ba`) |
| J6.6 | TERMINÉ TECHNIQUEMENT (`a34e5f3`) + Sync `.ai/` (`c00deba`) |
| J6.7a / B-111 | CONCEPTION TERMINÉE (`.ai/REPORTS/analyse_conception_2026-09-27_sauvegarde_distante_google_drive.md`) |
| J6.7b LOT 1 / B-112 | TERMINÉ TECHNIQUEMENT (`8bac07c`) + Sync `.ai/` (`2ade92a`) |
| J6.7b LOT 2 / B-112 | TERMINÉ TECHNIQUEMENT (`11d47cb`) + Sync `.ai/` (`a027245`) |
| J6.7b LOT 3A / B-112 | **TERMINÉ TECHNIQUEMENT** (`55067de`) — post-commit **VALIDÉ** |
| J6.7b LOT 3B | **NEXT / NOT STARTED** (attente d'autorisation d'exécution) |

## J6.7b LOT 3A — BackupViewModel & Startup Wiring (terminé)

Livré dans le commit `55067de` (3 fichiers techniques, +220 / -9, aucun `.ai/`) :
- **`BackupViewModel`** (`BackupViewModel.kt`) : injection Hilt de `RemoteBackupRepository`, `GoogleDriveAuthManager` et `PreferencesManager`. Exposition des `StateFlow` distants (`isRemoteBackupEnabled`, `isWifiOnlyBackupEnabled`, `lastRemoteBackupTime`, `lastRemoteBackupStatus`, `isGoogleConnected`, `googleAccountEmail`).
- **Commandes ViewModel** : `toggleRemoteBackup(enabled)` (persiste + schedule/cancel WorkManager), `toggleWifiOnlyBackup(wifiOnly)` (persiste + reschedule si actif), `signOutGoogleDrive()` (déconnexion Google), `triggerManualRemoteBackup()` (sauvegarde distante manuelle chiffrée avec le secret dérivé `deriveRemoteBackupPassword(boutique.managerCode)`).
- **Startup Wiring** : `CaisseApplication.kt` vérifie `preferencesManager.isRemoteBackupEnabled()` dans `onCreate()` et ré-enregistre le worker au démarrage de l'app sans toucher à `MainActivity.kt`.
- **Tests unitaires JVM** (`BackupViewModelTest.kt`) : 8 tests (4 originaux conservés + 4 nouveaux tests LOT 3A).
- 189/189 tests JVM ; `compileDebugKotlin` / KSP / **`assembleDebug` OK** ; APK debug généré.

Phase actuelle = **synchronisation documentaire AI-DOS J6.7b LOT 3A**.

## Encore ouvert dans le jalon 6

Sauvegarde distante :
- J6.7b LOT 3B (B-112-LOT3B) : Carte UI Google Drive dans `SettingsScreen` & i18n FR/EN. **NEXT / NOT STARTED**.
- J6.7b LOT 3C (B-112-LOT3C) : Restauration cloud dans `RestorationWizardScreen` & Recette Globale.
Type monétaire (6.8).

---
*Mis à jour le 2026-10-02 (clôture J6.7b LOT 3A, commit 55067de). Précédente fiche : 2026-09-28, J6.7b LOT 2.*
