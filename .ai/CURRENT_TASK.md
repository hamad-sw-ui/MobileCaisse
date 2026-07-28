# 🎯 TÂCHE EN COURS

**Tâche :** ⏳ **Validation par compilation réelle** — action requise de votre part.

Mode **développement prioritaire** (décision du 2026-07-28). J0 et `pinSalt`
livrés dans cette session ; le framework n'évolue plus que sur incident réel.

## Chemin critique

| # | Étape | Statut |
|---|---|---|
| 0 | **J0 — build propre** | ✅ livré — `CORRIGÉ (INSPECTION)` |
| 1 | Valider `BackupManager` | ⏳ **attend `make validate`** |
| 2 | Brancher `BackupManager` | ⛔ prérequis : 1, B-013, B-157 |
| 3 | Corriger `pinSalt` | ✅ livré — `CORRIGÉ (INSPECTION)` |
| 4 | Terminer J0 | ✅ livré |
| 5 | Développements fonctionnels (roadmap) | ⛔ après 2 |

> **Pourquoi J0 est passé avant l'étape 1** : 🧠 *déduit* — BUG-003 empêchait la
> compilation du **module entier**, donc l'exécution des 26 tests de
> `BackupManager`. J0 était un prérequis de l'étape 1, pas une digression.

## Livré cette session (11 tâches)

| Réf. | Contenu |
|---|---|
| **B-003** | `ComponentActivity` + thème **plateforme** — zéro dépendance ajoutée |
| B-001 | `gradlew` exécutable |
| B-002 | `proguard-rules.pro` (Room, SQLCipher, serialization, ML Kit) |
| B-004 | `sqlcipher` / `sqlite-ktx` dans le catalog |
| B-006 | `configuration-cache=false` + Kotlin in-process |
| B-007/008 | `conversation.txt` supprimé, 28 fichiers dé-versionnés, logo dans `res/` |
| B-100 | `README.md` racine |
| B-110 | « Exporter et partager » — textes seuls |
| **B-125** | `MIGRATION_25_26` alignée sur `StaffEntity` *(BUG-022)* |

Bugs passés en `CORRIGÉ (INSPECTION)` : **BUG-003, BUG-016, BUG-022**.

---

## ▶️ Commandes à exécuter

```bash
cd MobileCaisse
make image        # si pas encore fait
make verify
make validate     # compilation + analyses + 26 tests BackupManager
```

**Ce qui change par rapport aux sessions précédentes** : `make validate` ne
devrait **plus** échouer sur AppCompat. Si la compilation passe, les 26 tests de
`BackupManager` s'exécuteront pour la première fois.

Transmettez-moi la sortie, succès ou échec. J'appliquerai
`PROMPTS/analyse_resultats_build.md`.

### Ce que je ferai selon le résultat

| Résultat | Action |
|---|---|
| ✅ Tout vert | BUG-003/016/018/019/021/022/023 → **`CORRIGÉ (VALIDÉ)`**, puis B-013 + B-157, puis branchement |
| ❌ Erreurs de compilation | Analyse, correction, relance de toute la chaîne |
| ❌ Tests en échec | Le test a-t-il raison ? Correction du code, jamais de l'assertion |

---

*Mis à jour le 2026-07-28 (rév. 9 — mode développement).*
