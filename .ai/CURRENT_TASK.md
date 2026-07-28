# 🎯 TÂCHE EN COURS

**Tâche :**
Nouvelle règle §14 (analyse d'impact) intégrée au framework **et appliquée** au
branchement de `BackupManager`.

⏸️ **Développement de l'étape 5 suspendu** : l'analyse d'impact conclut
« développement : NON », étapes 3–4 non terminées.

## État du nouvel ordre d'exécution

| # | Étape | Statut |
|---|---|---|
| 1 | Réparer `BackupManager` | ✅ écrit — ⏳ `CORRIGÉ (INSPECTION)` |
| 2 | Tests prouvant son fonctionnement | ✅ 26 écrits — ⏳ non exécutés |
| 3 | Vérifier export → restauration | ⏳ **attend vos résultats** |
| 4 | Corriger jusqu'à fiabilité | ⏳ selon résultats |
| 5 | Brancher sur A, B, C | 📋 **analyse d'impact faite** — code suspendu |
| 6 | Supprimer l'ancien mécanisme | ⛔ + **prérequis B-013 découvert** |
| 7 | Corriger `staff.pinSalt` | ⛔ |
| 8 | Reprendre J0 | ⛔ |

## Ce que l'analyse d'impact a révélé (avant d'écrire une ligne)

Rapport : `REPORTS/analyse_impact_2026-07-28_branchement_backupmanager.md`

| Découverte | Conséquence |
|---|---|
| 🔴 **R8** — BUG-011 (checkpoint WAL) non corrigé | **B-013 devient prérequis de l'étape 6** : supprimer l'ancien mécanisme laisserait un unique chemin potentiellement incohérent |
| 🔴 **R2** — `restoreDatabase` fait `db.close()` **avant** la copie | Un échec rend l'application inutilisable → restaurer en fichier temporaire (**B-140**) |
| 🔴 **R1** — aucune distinction `.zip` / `.db` | Confusion possible → détection par magie de fichier (**B-141**) |
| ❌ **QA** — aucun test ne couvre les 5 chemins actuels | Vérification manuelle obligatoire avant l'étape 6 |
| 🔴 Chemins **D et E** structurellement inéligibles | `SetupScreen` et `BackupWorker` s'exécutent **sans interface** : aucun mot de passe saisissable. Deux formats coexisteront (**B-144**) |
| ⚠️ `MainViewModel` unique, partagé par ~30 écrans | Interdiction de modifier une signature existante à l'étape 5 |

**5 nouvelles tâches** (B-140 → B-144) issues de cette analyse, toutes
identifiées **avant** toute modification de code.

**Contraintes :**
- `CODING_RULES.md` §14 : aucune modification sans analyse d'impact préalable.
- `CODING_RULES.md` §13 : aucun correctif terminé sans compilation + tests.
- ⛔ Aucun branchement avant les 26 tests verts.

---

## ▶️ Commandes à exécuter

```bash
cd MobileCaisse
make verify
./docker/scripts/test.sh "*BackupManager*"    # attendu : 26 tests, 0 échec
make validate                                  # détection de régressions
```

⚠️ `make validate` échouera probablement sur **BUG-003** (`androidx.appcompat`) :
défaut **préexistant**, sans rapport avec `BackupManager`.

À réception, j'applique `PROMPTS/analyse_resultats_build.md`.

---

*Mis à jour le 2026-07-28 (rév. 7 — règle §14 appliquée).*
