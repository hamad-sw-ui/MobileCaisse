# 🗺️ ROADMAP — Plan de complétion

\u003e **Statut : ✅ VALIDÉE le 2026-07-28** — décisions D1 à D4 reçues.
\u003e Point de départ retenu : **J0 (build propre + nettoyage technique)**, puis
\u003e enchaînement direct sur **J1.1 + J1.2** (intégrité des migrations).

## Décisions du responsable (2026-07-28)

| Réf. | Décision | Conséquence |
|---|---|---|
| **D1** | Bases \u003c v18 : perte assumée, **avec accord de l\u0027utilisateur** | B-011 \u003d détection + consentement + export de courtoisie + recréation. `fallbackToDestructiveMigration` reste proscrit |
| **D2** | **Les deux** : renommer l\u0027existant *et* préparer un vrai service distant | B-110 (renommage seul, sans toucher à la logique) + B-111 (analyse Drive/Dropbox) + B-112 (implémentation) |
| **D3** | Démarrer par **J0**, puis **J1.1 + J1.2** | Ordre confirmé ci-dessous |
| **D4** | Le responsable dispose d\u0027Android Studio et exécute builds/tests | Chaque livraison s\u0027accompagne des **commandes exactes** à exécuter |
| **Prod** | **Aucun utilisateur en production** | J1 perd son caractère de sauvetage : c\u0027est un chantier de qualité, pas d\u0027urgence. Latitude pour refondre la chaîne de migrations |

---

## Jalon D — Environnement Docker *(Phase 6, livré le 2026-07-28)* ✅

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| D.1 | Créer `docker/` (image, compose, scripts) | B-130 | ✅ |
| D.2 | Documenter `.ai/DEV_ENVIRONMENT.md` | B-131 | ✅ |
| D.3 | Intégrer aux checklists et règles | B-132 | ✅ |
| D.4 | **Première exécution `make image \u0026\u0026 make verify`** | B-133 | ✅ |

---

## Vue d\u0027ensemble des dépendances

```
JD Environnement Docker  ◄── Phase 6, préalable à tout
      │
      ▼
J0 Build vérifiable ✅
      │
      ▼
J1 Intégrité des données ✅
      │
      ▼
J2 Sécurité ✅
      │
      ▼
J3 Filet de tests ✅
      │
      ▼
J4 Hilt + assainissement ✅
      │
      ▼
J5 Découpage Repository / ViewModel ⏳ (En cours)
      │
      ▼
J6 Fonctionnalités manquantes
      │
      ▼
J7 Industrialisation (CI, release)
```

---

## Jalon 4 — Hilt et assainissement ✅

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 4.1 | Mise en place de Hilt | B-030 | ✅ |
| 4.2 | `DatabaseModule` | B-031 | ✅ |
| 4.4 | `@HiltViewModel` | B-033 | ✅ |
| 4.5 | `@HiltWorker` | B-034 | ✅ |

---

## Jalon 5 — Découpage architectural ⏳

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 5.1 | Extraire `SalesRepository` | B-050b | ✅ |
| 5.2 | Extraire `StockRepository` (Inventory) | B-050c | ⏳ Cœur Stock OK / Commit 2 ⏸ |
| 5.3 | Extraire `CustomerRepository` | B-050 | □ |
| 5.4 | Extraire `CashRepository` | B-050 | □ |

---

## Jalon 6 — Fonctionnalités manquantes □

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 6.1 | Écran personnel | B-070 | □ |
| 6.2 | Sessions de caisse | B-071 | □ |

---

*Document mis à jour le 2026-08-13 (Post-Commit 1 Inventory).*
