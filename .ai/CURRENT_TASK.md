# 🎯 TÂCHE EN COURS

**Tâche :**
Phase 7 intégrée au framework **et appliquée** au branchement de `BackupManager`
(niveau **C**). Le workflow complet a été déroulé : impact → conception → débat
→ opportunités.

⏸️ **Développement toujours suspendu** — trois prérequis non satisfaits.

## Où en est le nouvel ordre d'exécution

| # | Étape | Statut |
|---|---|---|
| 1 | Réparer `BackupManager` | ✅ écrit — `CORRIGÉ (INSPECTION)` |
| 2 | Tests | ✅ 26 écrits — non exécutés |
| 3 | Vérifier export → restauration | ⏳ **attend vos résultats** |
| 4 | Corriger jusqu'à fiabilité | ⏳ |
| 5 | Brancher A, B, C | 📋 impact + conception + débat faits — **code suspendu** |
| 6 | Supprimer l'ancien mécanisme | ⛔ |
| 7 | `staff.pinSalt` | ⛔ |
| 8 | J0 | ⛔ |

## Prérequis bloquants avant l'étape 5

| # | Prérequis | Origine |
|---|---|---|
| 1 | 26 tests `BackupManager` verts | étapes 3–4 |
| 2 | **B-013** — checkpoint WAL avant copie | ❌ bloquant Expert Room |
| 3 | **B-157** — protocole de vérification manuelle des 5 chemins | ❌ bloquant Expert QA |

## Ce que la Phase 7 a produit

📄 4 rapports : `analyse_impact` · `analyse_conception` · `debat_technique` ·
`opportunites` (tous datés du 2026-07-28, sujet `branchement_backupmanager` /
`sauvegarde`).

**Découvertes, avant toute ligne de code :**

| Source | Découverte |
|---|---|
| Conception | Solution A (tout migrer) **violerait D2-C** — clé recalculable depuis le `managerCode`. Écartée. |
| Conception | Solution C retenue : corriger les fondations d'abord, brancher ensuite |
| Débat (Room) | 🟠 **BUG-024** — fenêtre de concurrence pendant la restauration |
| Débat (QA) | ❌ aucun état de référence : « aucune régression » serait invérifiable |
| Débat (Sécurité) | fenêtre où deux exports coexistent, dont un non protégé → masquer dès l'étape 7 |
| Opportunités | 13 améliorations, dont 5 duplications de `ACTION_SEND` et 2 exports CSV concurrents |

**14 nouvelles tâches** (B-145 → B-158). Aucune implémentée : proposées.

⚠️ **Une objection du débat s'est révélée fausse** à la vérification (« le
singleton `AppDatabase` n'est pas invalidé » — il l'est bien, `AppDatabase.kt:138-142`).
Corrigée, trace conservée. → règle ajoutée à §15.2.

**Objectif :**
Obtenir 26/26 tests verts, puis traiter B-013 et B-157, puis brancher.

---

## ▶️ Commandes à exécuter

```bash
cd MobileCaisse
make verify
./docker/scripts/test.sh "*BackupManager*"    # attendu : 26 tests, 0 échec
make validate
```

⚠️ `make validate` échouera probablement sur **BUG-003** (`androidx.appcompat`),
préexistant et sans rapport.

## ❓ Arbitrage demandé

Le rapport d'opportunités propose **B-155 — double saisie du mot de passe à
l'export**. Coût très faible, et cela supprime le mode d'échec le plus probable
de tout le système : une faute de frappe rend l'archive **définitivement**
illisible. → **L'intégrer à l'étape 5, ou le laisser au backlog ?**

---

*Mis à jour le 2026-07-28 (rév. 8 — Phase 7).*
