# 🎯 TÂCHE EN COURS

**Tâche :** ⛔ **BLOCAGE — compilation requise sur votre machine.**

Le chemin critique est déroulé jusqu'au bout de ce qui est faisable sans
compilateur. **9 bugs** sont en attente de validation.

## Chemin critique — état

| # | Étape | Statut |
|---|---|---|
| 0 | J0 — build propre | ✅ écrit |
| 1 | Valider `BackupManager` | ⛔ **compilation requise** |
| 2 | Brancher `BackupManager` | ✅ écrit (A, B, C) |
| 3 | `pinSalt` | ✅ écrit |
| 4 | Terminer J0 | ✅ |
| 5 | Développements fonctionnels | ⛔ **bloqué par 1** |

## Pourquoi je m'arrête ici

9 bugs en `CORRIGÉ (INSPECTION)` : BUG-003, 011, 016, 017, 018, 019, 021, 022,
023, 024, 025. **Aucune ligne n'a rencontré de compilateur.**

Poursuivre sur la roadmap fonctionnelle reviendrait à empiler du code non
vérifié sur du code non vérifié — exactement le défaut qui a produit
`BackupManager` (livré « fonctionnel », en réalité non compilable).

## ▶️ Commandes

```bash
cd MobileCaisse
make image && make verify
make validate
```

**Attendu** : ~35 tests unitaires (26 `BackupManager` + 9 `BackupFormat`),
plus les tests existants.

À réception, j'applique §19 : classement par cause racine, correction groupée
des causes indépendantes, **une seule** recompilation, rapport §19.6.

## Après déblocage — file d'attente

1. Étape 6 : retirer l'ancien mécanisme d'export non protégé
2. B-070 : écran de gestion du personnel *(entité + DAO existent, aucune UI)*
3. J1 : `exportSchema = true` + refonte de la chaîne de migrations
4. B-090/091 : tests `SmsParser` et `FeeCalculator`

---

*Mis à jour le 2026-07-28 (rév. 12).*
