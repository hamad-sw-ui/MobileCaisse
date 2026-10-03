# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire post-J6.7b LOT 3C-1. Aucune implémentation en cours. J6.7b LOT 3C-1 est clôturé techniquement (`5575241`). **J6.7b LOT 3C-2 n'est pas commencé.**

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `557524168574ebce25f0e0676c1eaab797c6e06c` |
| Commit | `feat(j6.7): implement cloud restore foundation` |
| Parent | `1079b66e74281698c171bcc18c27dcf575b56563` |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| J6.5 | TERMINÉ TECHNIQUEMENT (`498b9ba`) |
| J6.6 | TERMINÉ TECHNIQUEMENT (`a34e5f3`) + Sync `.ai/` (`c00deba`) |
| J6.7a / B-111 | CONCEPTION TERMINÉE (`.ai/REPORTS/analyse_conception_2026-09-27_sauvegarde_distante_google_drive.md`) |
| J6.7b LOT 1 / B-112 | TERMINÉ TECHNIQUEMENT (`8bac07c`) + Sync `.ai/` (`2ade92a`) |
| J6.7b LOT 2 / B-112 | TERMINÉ TECHNIQUEMENT (`11d47cb`) + Sync `.ai/` (`a027245`) |
| J6.7b LOT 3A / B-112 | TERMINÉ TECHNIQUEMENT (`55067de`) + Sync `.ai/` (`84a7c84`) |
| J6.7b LOT 3B / B-112 | TERMINÉ TECHNIQUEMENT (`0a8c13b`) + Sync `.ai/` (`1079b66`) |
| J6.7b LOT 3C-1 / B-112 | **TERMINÉ TECHNIQUEMENT** (`5575241`) — post-commit **VALIDÉ** |
| J6.7b LOT 3C-2 | **NEXT / NOT STARTED** (attente d'autorisation d'exécution) |

## J6.7b LOT 3C-1 — Restauration Cloud (Fondations) (terminé)

Livré dans le commit `5575241` (4 fichiers techniques, +135 / -0, aucun `.ai/`) :
- **`RemoteBackupRepository`** : wrappers `listRemoteBackups()` et `downloadRemoteBackup(fileId, destFile)` déléguant aux APIs REST Google Drive.
- **`BackupViewModel`** : exposition du StateFlow `cloudBackups`, commande `fetchCloudBackups()` et méthode `restoreCloudBackup(context, remoteItem, userRole, customPassword, onRestored)`.
- **Sécurité & Nettoyage** : téléchargement binaire temporaire isolé dans `cacheDir` (`temp_cloud_restore_*.db.enc`) nettoyé inconditionnellement dans un bloc `finally`.
- **Secret & Dérivation** : réutilise la dérivation déterministe `RemoteBackupRepository.deriveRemoteBackupPassword(boutique.managerCode)` (`"${managerCode.trim()}a1A!"`) avec support optionnel du paramètre `customPassword`.
- **Chaîne d'import réutilisée 100%** : délègue à `MainRepository.importEncryptedBackup()` -> `BackupManager.importBackupWithPassword()` -> `restoreDatabase()`. Zero second mécanisme de restauration créé.
- **Tests unitaires** : 4 nouveaux tests unitaires (2 dans `RemoteBackupRepositoryTest`, 2 dans `BackupViewModelTest`).
- 193/193 tests JVM ; `compileDebugKotlin` / KSP / **`assembleDebug` OK** ; APK debug généré.

Phase actuelle = **synchronisation documentaire AI-DOS J6.7b LOT 3C-1**.

## Encore ouvert dans le jalon 6

Sauvegarde distante :
- J6.7b LOT 3C-2 (B-112-LOT3C2) : Restauration Cloud UI dans `RestorationWizardScreen` & Recette Globale. **NEXT / NOT STARTED**.
Type monétaire (6.8).

---
*Mis à jour le 2026-10-03 (clôture J6.7b LOT 3C-1, commit 5575241). Précédente fiche : 2026-10-02, J6.7b LOT 3B.*
