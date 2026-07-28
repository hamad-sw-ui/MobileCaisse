# 🎯 TÂCHE EN COURS

**Tâche :**
**Pré-jalon 0 — Fiabiliser puis brancher `BackupManager`** (priorité fixée par le
responsable le 2026-07-28), puis **corriger la contradiction `staff.pinSalt`**.

⛔ **BLOCAGE SIGNALÉ — en attente d'arbitrage.**
La cartographie demandée est faite (voir ci-dessous), mais l'inspection ligne à
ligne de `BackupManager` avant branchement a révélé **3 défauts bloquants**.
Brancher le module en l'état produirait des sauvegardes **irrécupérables**.

| Réf. | Bug | Gravité |
|---|---|---|
| B-120 | AES-GCM : tag concaténé 2× → tout import échoue *(BUG-018)* | 🔴 |
| B-121 | La base n'est pas chiffrée par le mot de passe, seules les métadonnées le sont *(BUG-019)* | 🟠 |
| B-122 | `java.time.Instant` = API 26, incompatible minSdk 24 sans desugaring *(BUG-020)* | 🟠 |
| B-123 | `databaseVersion = 27` en dur alors que le schéma est en v28 *(BUG-021)* | 🟡 |

**Ordre d'exécution proposé :**
1. B-120 → B-123 : réparer `BackupManager`
2. B-124 : tests unitaires (aller-retour, mauvais mot de passe, archive corrompue)
3. B-101 : brancher sur `RestorationWizardScreen`, `SettingsScreen`, `ClosureScreen`
4. B-125 : corriger `staff.pinSalt` (verdict rendu : c'est la **migration** qui a tort)
5. Puis **J0**

**Objectif :**
Remplacer l'export/import non protégé par un mécanisme réellement chiffré par
mot de passe utilisateur, **vérifié par des tests**, sans perdre aucune
fonctionnalité existante.

**Contraintes :**
- **Aucune régression** sur les 4 parcours de sauvegarde existants.
- **Mot de passe saisi manuellement à chaque export** (décision D2-C) —
  jamais dérivé du `managerCode`.
- Ne pas régresser les 5 correctifs de sécurité déjà en place.
- Ne pas toucher aux noms de fonctions internes (décision B-110).
- ⚠️ Environnement sans JDK/SDK : je fournis les commandes, le responsable
  exécute (D4).

---

## Cartographie demandée : qui appelle l'export/import aujourd'hui

### Chemin A — Export manuel (Paramètres) 🎯 *cible principale*
```
SettingsScreen.kt:119   Button « Sauvegarder / Migrer (DB) »
  └─ MainViewModel.kt:463  syncToCloud(context)
       └─ MainRepository.kt:889  syncToCloud()
            ├─ copie brute du .db dans cacheDir
            └─ Intent.ACTION_SEND  « Synchroniser vers le Cloud / Email »
```

### Chemin B — Export depuis la clôture 🎯 *cible*
```
ClosureScreen.kt:144    Button « Sauvegarde Totale (.db) »
  └─ ClosureScreen.kt:198  fun backupDatabase(context)   ← fonction LOCALE à l'écran,
                                                            hors ViewModel/Repository
       ├─ dbFile.copyTo(...)
       └─ Intent.ACTION_SEND
```
⚠️ Viole MVVM : accès direct au système de fichiers depuis un Composable.

### Chemin C — Restauration manuelle 🎯 *cible principale*
```
RestorationWizardScreen.kt:112   Assistant 2 étapes (choix fichier → confirmation)
  └─ MainViewModel.kt:433  restoreDatabase(tempFile)
       └─ MainRepository.kt:801  restoreDatabase()  → db.close() + copie brute
```

### Chemin D — Restauration du miroir au premier lancement
```
SetupScreen.kt:148   Dialog « Anciennes données trouvées »
  └─ MainRepository.restoreDatabase(context, caisse_mirror.db)
```
⚠️ Miroir interne produit par `BackupWorker` : **doit rester au format brut**
(pas de mot de passe utilisateur disponible en arrière-plan).

### Chemin E — Sauvegarde automatique quotidienne
```
BackupWorker.kt:23,30   → MainRepository.backupDatabase()  ×2
```
⚠️ **Ne pas migrer vers `BackupManager`** : aucun mot de passe saisissable dans
un worker. Reste une copie locale, protégée par SQLCipher.

### Synthèse
| Chemin | Action | Motif |
|---|---|---|
| A — Paramètres | ✅ migrer vers `BackupManager` | export sortant de l'appareil |
| B — Clôture | ✅ migrer + remonter dans le ViewModel | export sortant |
| C — Assistant restauration | ✅ migrer vers `BackupManager` | import d'archive |
| D — Miroir au setup | ❌ conserver | pas de mot de passe disponible |
| E — Worker quotidien | ❌ conserver | pas de mot de passe disponible |

Les chemins C et D devront **détecter le format** (ZIP `BackupManager` vs `.db`
brut) pour rester rétrocompatibles.

---

*Mis à jour le 2026-07-28 (rév. 3).*
