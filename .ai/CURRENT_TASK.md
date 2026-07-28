# 🎯 TÂCHE EN COURS

**Tâche :** ⏳ **Validation par compilation réelle** — action requise de votre part.

Framework **gelé** (§20). Mode livraison : cette session a produit du code, pas
des règles.

## Chemin critique

| # | Étape | Statut |
|---|---|---|
| 0 | J0 — build propre | ✅ `CORRIGÉ (INSPECTION)` |
| 1 | Valider `BackupManager` | ⏳ **attend `make validate`** |
| 2 | Brancher `BackupManager` | 🟡 prérequis B-013 ✅ · B-157 ⏳ · tests ⏳ |
| 3 | `pinSalt` | ✅ `CORRIGÉ (INSPECTION)` |
| 5 | Développements fonctionnels | ⛔ après 2 |

## Livré cette session

**B-013 / BUG-011 — checkpoint WAL.** `PRAGMA wal_checkpoint(FULL)` avant toute
copie, avec **échec explicite** si le checkpoint est bloqué : une sauvegarde
silencieusement incomplète est pire qu'une absence de sauvegarde. Copie via
fichier `.tmp` renommé en fin d'opération.

**B-140 / B-156 / BUG-024 — restauration sûre.** La copie est intégralement
écrite dans `cacheDir/restore_staging.db` et sa taille vérifiée **avant** que
`db.close()` ne soit appelé. L'ancienne implémentation fermait la base en
premier : un échec de copie laissait l'application sans base exploitable.
Les fichiers `-wal`/`-shm` de l'ancienne base sont purgés.

→ **Le prérequis bloquant de l'étape 2 (avis ❌ Expert Room) est levé.**

Reste avant branchement : tests verts (étape 1) et **B-157** (protocole de
vérification manuelle — exigence ❌ Expert QA).

---

## ▶️ Commandes

```bash
cd MobileCaisse
make image && make verify
make validate
```

À réception : classement §19 par cause racine, correction groupée des causes
indépendantes, **une seule** recompilation, rapport §19.6.

---

*Mis à jour le 2026-07-28 (rév. 11 — framework gelé, mode livraison).*
