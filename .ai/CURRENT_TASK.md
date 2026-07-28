# 🎯 TÂCHE EN COURS

**Tâche :**
**Étapes 1–2 du nouvel ordre livrées. ⏳ En attente de validation par
compilation réelle.**

> **État officiel : « Corrigé par inspection statique, en attente de validation
> par compilation réelle. »**
> Aucun des bugs BUG-018 à BUG-023 n'est considéré comme résolu.

## Nouvel ordre d'exécution

| # | Étape | Statut |
|---|---|---|
| 1 | Réparer `BackupManager` | ✅ écrit — ⏳ non compilé |
| 2 | Tests prouvant son fonctionnement | ✅ 26 tests écrits — ⏳ non exécutés |
| 3 | Vérifier export → restauration | ⏳ **attend vos résultats** |
| 4 | Corriger jusqu'à fiabilité complète | ⏳ selon résultats |
| 5 | Brancher sur A, B, C | ⛔ bloqué par 3–4 |
| 6 | Supprimer l'ancien mécanisme | ⛔ bloqué par 5 |
| 7 | Corriger `staff.pinSalt` | ⛔ après 6 |
| 8 | Reprendre J0 | ⛔ après 7 |

## Ce qui est démontré, ce qui ne l'est pas

| Élément | Statut | Preuve |
|---|---|---|
| Logique cryptographique du format v2 | ✅ **démontrée** | `verify_backup_format.py` — 16/16, exécuté |
| BUG-018 reproduit puis corrigé *dans le format* | ✅ **démontré** | contrôle 6/16 |
| **Compilation du module Kotlin** | ❌ **non prouvée** | aucun JDK dans l'environnement |
| **Exécution des 26 tests Kotlin** | ❌ **non prouvée** | aucun Docker dans l'environnement |
| Compatibilité **réelle** API 24 | ❌ **non prouvée** | exige un appareil API 24 |
| Absence de régression | ❌ **non prouvée** | aucun build de référence |

**Objectif :**
Convertir `CORRIGÉ (INSPECTION)` en `CORRIGÉ (VALIDÉ)` pour BUG-018/019/021/023.
BUG-020 restera partiellement validé sans appareil API 24.

**Contraintes :**
- `CODING_RULES.md` §13 : compilation réelle + tests + aucune régression.
- ⛔ Aucun branchement avant tests verts.
- Ne pas régresser les 5 correctifs de sécurité (`SECURITY.md` §0).

---

## ▶️ Commandes à exécuter

```bash
cd MobileCaisse
make verify
./docker/scripts/test.sh "*BackupManager*"     # attendu : 26 tests, 0 échec
```

Puis, pour détecter les régressions ailleurs :
```bash
make validate
```

Transmettez-moi les sorties **complètes**, y compris en cas d'échec.
⚠️ `make validate` échouera probablement sur **BUG-003** (`androidx.appcompat`),
défaut **préexistant et sans rapport** avec `BackupManager` — attendu, à ne pas
confondre avec un échec du correctif.

À réception, j'applique `PROMPTS/analyse_resultats_build.md` :
analyser → corriger → relancer toute la chaîne → ne déclarer résolu qu'après
réussite complète.

Validation croisée facultative, sans Docker :
```bash
pip install cryptography
python3 tools/verification/verify_backup_format.py    # attendu : 16/16
```

---

*Mis à jour le 2026-07-28 (rév. 6 — terminologie de validation corrigée).*
