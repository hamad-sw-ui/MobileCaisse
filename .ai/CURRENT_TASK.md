# 🎯 TÂCHE EN COURS

**Tâche :** enregistrement documentaire de la décision humaine d'architecture issue de l'étude J6.8 / B-077 Lot 1. Aucune implémentation de code en cours.

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `1f011a0642447a7218c3f27459f1cdb3706cfc84` |
| Commit technique | `de9637c8641c7bc2add6109d6026d8a6785e2329` (`feat(j6.7): implement cloud restore UI`) |
| Commit doc J6.7b | `1f011a0642447a7218c3f27459f1cdb3706cfc84` (`docs(ai-dos): close J6.7b LOT 3C-2`) |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| J6.5 | TERMINÉ TECHNIQUEMENT (`498b9ba`) |
| J6.6 | TERMINÉ TECHNIQUEMENT (`a34e5f3`) + Sync `.ai/` (`c00deba`) |
| J6.7a / B-111 | CONCEPTION TERMINÉE (`.ai/REPORTS/analyse_conception_2026-09-27_sauvegarde_distante_google_drive.md`) |
| J6.7b / B-112 | TERMINÉ TECHNIQUEMENT (`de9637c`) + Sync `.ai/` (`1f011a0`) |
| J6.8 / B-077 Lot 1 | **ÉTUDE TERMINÉE & VALIDÉE** (`STUDY AUDIT GO`) — **DÉCISION HUMAINE CONSIGNÉE** |
| J6.8b (Migration DB) | **NON REQUIS / NON DÉCIDÉ** (décision de maintenir le stockage SQLite `Double`) |

## J6.8 / B-077 Lot 1 — Étude d'Architecture Monétaire & Décision Humaine (terminé)

Livrable rédigé : `.ai/REPORTS/analyse_conception_2026-10-04_passage_type_monetaire_exact.md` (`STUDY AUDIT GO`).
- **Inventaire exhaustif du code :** 22 entités Room dans `AppDatabase.kt` (16 avec `Double`, 13 avec des champs monétaires FCFA, 1 avec un taux/pourcentage `BoutiqueEntity.taxRate`, 2 avec des quantités physiques seules `recipes`/`stock_movements`, 6 sans `Double`).
- **Décision humaine d'architecture retenue :**
  1. **Maintien du stockage SQLite monétaire existant en `Double`** : pas de migration physique de base de données vers `Long` ou `BigDecimal` à ce stade.
  2. **Pas de modification des colonnes SQLite existantes.**
  3. **Sécurisation des calculs :** renforcement des règles d'arrondi explicites (`Math.round()`) aux points de calculs métier (TVA, remises, commissions MoMo, totals).
  4. **Value Class Domaine :** une Inline Value Class `Money(val amount: Double)` pourra être étudiée ultérieurement comme couche d'encapsulation domaine sans modification de schéma SQLite.
  5. **Toute migration physique future** vers `Long` ou `BigDecimal` nécessitera un chantier dédié séparé et une nouvelle décision.

Phase actuelle = **synchronisation documentaire AI-DOS J6.8 / B-077 Lot 1**.

## Prochaines étapes

- Jalon 7 — Industrialisation (CI, tests, release).
- Décisions et arbitrages fonctionnels ultérieurs.

---
*Mis à jour le 2026-10-05 (enregistrement de la décision humaine J6.8 / B-077). Précédente fiche : 2026-10-04, J6.7b LOT 3C-2.*
