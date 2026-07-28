# 🎯 TÂCHE EN COURS

**Tâche :**
**Étape 1–4 du nouvel ordre : réparer `BackupManager` et prouver son
fonctionnement.** Code livré, tests écrits, logique validée par un harnais
indépendant. ⏳ **En attente d'exécution des tests dans Docker.**

## Nouvel ordre d'exécution (validé le 2026-07-28)

| # | Étape | Statut |
|---|---|---|
| 1 | Réparer complètement `BackupManager` | ✅ **fait** — BUG-018/019/020/021 + BUG-023 |
| 2 | Ajouter les tests prouvant son fonctionnement | ✅ **fait** — 26 tests Kotlin |
| 3 | Vérifier export → restauration | ⏳ **attend `make test`** |
| 4 | Corriger jusqu'à fiabilité complète | ⏳ selon résultats |
| 5 | Brancher sur A, B, C | ⛔ bloqué par 3 |
| 6 | Supprimer l'ancien mécanisme | ⛔ bloqué par 5 |
| 7 | Corriger `staff.pinSalt` | ⛔ après 6 |
| 8 | Reprendre J0 | ⛔ après 7 |

## Ce qui a été corrigé

| Bug | Correctif |
|---|---|
| **BUG-023** 🔴 *(nouveau)* | Le fichier **ne compilait pas** : `Result<Unit>` déclaré, `Result<Result<Unit>>` produit. Preuve qu'il n'a jamais été compilé |
| **BUG-018** 🔴 | Tag GCM concaténé deux fois → tout import échouait. Format v2 : `IV \|\| AES-GCM(payload)` |
| **BUG-019** 🟠 | La base est désormais chiffrée par la clé du mot de passe, **en flux** (8 Ko) |
| **BUG-020** 🟠 | `Instant.now()` (API 26) **et** `readAllBytes()` (API 33, non repéré à l'audit) remplacés |
| **BUG-021** 🟡 | `databaseVersion` fourni par l'appelant |

**Renforcements ajoutés** : manifeste en clair mais **authentifié via l'AAD** ;
checksum SHA-256 vérifié après déchiffrement ; nettoyage des fichiers partiels
en cas d'échec ; `peekMetadata()` pour un aperçu avant restauration ; écart
d'identité **signalé** et non bloquant (la décision revient à l'UI).

## Preuves disponibles

1. **26 tests unitaires** — `app/src/test/.../BackupManagerTest.kt`, JVM pur,
   exécutables dans Docker sans appareil.
2. **Harnais indépendant** — `tools/verification/verify_backup_format.py` :
   réimplémente le format en Python, **16/16 contrôles réussis**, BUG-018
   reproduit puis corrigé. Exécuté avec succès.

**Objectif :**
Obtenir 26/26 tests verts dans Docker avant tout branchement.

**Contraintes :**
- ⛔ **Ne pas brancher** tant que les tests Kotlin ne sont pas verts (votre consigne).
- Aucune dépendance ajoutée au projet (vérifié : `git diff` sur `libs.versions.toml` vide).
- Ne pas régresser les 5 correctifs de sécurité (`SECURITY.md` §0).
- L'agent n'a ni Docker ni JDK : exécution par le responsable (D4).

---

## ▶️ Commandes à exécuter

```bash
cd MobileCaisse
make image                              # si pas déjà fait
make verify
./docker/scripts/test.sh "*BackupManager*"
```

Attendu : **26 tests, 0 échec**. Transmettez-moi la sortie — y compris en cas
d'échec, ce qui est possible : le code n'a jamais été compilé.

Validation croisée facultative, sans Docker :
```bash
pip install cryptography
python3 tools/verification/verify_backup_format.py   # attendu : 16/16
```

---

*Mis à jour le 2026-07-28 (rév. 5).*
