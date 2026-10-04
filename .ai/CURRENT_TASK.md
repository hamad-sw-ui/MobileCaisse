# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire post-J6.7b LOT 3C-2. Aucune implémentation en cours. J6.7b LOT 3C-2 est clôturé techniquement (`de9637c`).

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `de9637c8641c7bc2add6109d6026d8a6785e2329` |
| Commit | `feat(j6.7): implement cloud restore UI` |
| Parent | `65eb2a57e62e01784e4f4137b0df54a08f86e8b8` |
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
| J6.7b LOT 3C-1 / B-112 | TERMINÉ TECHNIQUEMENT (`5575241`) — post-commit **VALIDÉ** |
| J6.7b LOT 3C-2 / B-112 | **TERMINÉ TECHNIQUEMENT** (`de9637c`) — post-commit **VALIDÉ** |

## J6.7b LOT 3C-2 — Restauration Cloud UI & Recette Globale (terminé)

Livré dans le commit `de9637c` (4 fichiers techniques, +282 / -44, aucun `.ai/`) :
- **`RestorationWizardScreen.kt`** :
  - Intégration d'un `TabRow` pour basculer entre l'onglet "Fichier local" (index 0) et "Google Drive Cloud" (index 1).
  - Conservation intégrale du flux local existant (sélection via `ActivityResultContracts.GetContent()`, étape 2, confirmation).
  - Mode Cloud : affichage de la liste des sauvegardes distantes (`cloudBackups`), bouton de rafraîchissement manuel (`action_refresh_cloud_list`), déclenchement de `fetchCloudBackups()` sur activation de l'onglet ou changement d'état.
  - Sélection d'un élément distant (`selectedCloudItem`), passage à l'étape 2 avec écran d'avertissement et confirmation obligatoire avant écrasement.
  - Exécution de la restauration via `viewModel.restoreCloudBackup()` et redirection vers `splash` (`navController.navigate("splash") { popUpTo(0) }`) après succès.
  - Fallback vers `BackupPasswordDialog` pour la saisie d'un `customPassword` en cas d'échec de la dérivation automatique.
  - Réutilisation à 100% des mécanismes Google Sign-In existants et de la chaîne d'import/restauration.
- **`BackupViewModel.kt`** :
  - Remplacement de `catch (e: Exception)` par `catch (e: Throwable)` autour de la planification WorkManager dans `setRemoteBackupEnabled` et `scheduleRemoteBackupIfEnabled` (seule modification, audité et classé JUSTIFIÉ ET SÛR pour les tests unitaires JVM purs).
- **Ressources i18n FR/EN** :
  - 6 clés ajoutées avec 100% de parité (`restoration_tab_local`, `restoration_tab_cloud`, `label_cloud_backups_title`, `label_no_cloud_backups`, `action_refresh_cloud_list`, `status_downloading_cloud`).
- **Accessibilité statique** :
  - Zones tactiles >= 48dp sur contrôles et éléments de liste, `contentDescription` explicite sur bouton de rafraîchissement, icônes décoratives non annoncées (`TalkBack manual : NON TESTÉ`).
- **Tests & Validation** :
  - 193/193 tests JVM PASSED ; `compileDebugKotlin` SUCCESS ; `kspDebugKotlin` SUCCESS ; **`assembleDebug` SUCCESS** (APK debug).
- **Risques résiduels documentés** :
  - Recette cloud end-to-end réelle non effectuée sur appareil physique avec compte Google réel et réseau actif ; TalkBack manuel non testé.

Phase actuelle = **synchronisation documentaire AI-DOS J6.7b LOT 3C-2**.

## Encore ouvert dans le jalon 6

- Type monétaire (6.8 / B-077).

---
*Mis à jour le 2026-10-04 (clôture J6.7b LOT 3C-2, commit de9637c). Précédente fiche : 2026-10-03, J6.7b LOT 3C-1.*
