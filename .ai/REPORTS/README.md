# 📊 RAPPORTS

Ce dossier contient les **modèles** de rapports et les **rapports datés**
produits au fil du projet.

## Convention de nommage

```
rapport_<type>_<AAAA-MM-JJ>.md
```

Exemples : `rapport_securite_2026-08-15.md`, `rapport_analyse_2026-09-02.md`.

## Modèles disponibles

| Modèle | Quand le produire |
|---|---|
| `MODELE_rapport_analyse.md` | Avant toute décision structurante ou refactor important |
| `MODELE_rapport_securite.md` | À chaque modification de crypto, permission, accès — et avant chaque release |
| `MODELE_rapport_performance.md` | Avant/après optimisation, ou en cas de lenteur signalée |
| `MODELE_rapport_couverture_tests.md` | À chaque jalon de la roadmap |
| `MODELE_rapport_architecture.md` | À chaque évolution structurelle (Hilt, découpage) |

## Rapports produits

| Date | Fichier | Type |
|---|---|---|
| 2026-07-28 | `rapport_analyse_2026-07-28_audit_initial.md` | Analyse — audit initial du dépôt |

## Règles

- Un rapport est **daté et figé** : on n'écrase jamais un rapport existant.
- Un rapport constate des **faits vérifiables**, pas des impressions.
- Si une mesure n'a pas pu être exécutée, l'écrire — ne jamais l'inventer.
- Tout rapport structurant est référencé depuis `PROGRESS.md`.
