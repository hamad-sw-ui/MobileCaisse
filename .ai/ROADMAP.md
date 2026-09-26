# 🗺️ ROADMAP — Plan de complétion

> **Statut : ✅ VALIDÉE le 2026-07-28** — décisions D1 à D4 reçues.
> Point de départ retenu : **J0 (build propre + nettoyage technique)**, puis
> enchaînement direct sur **J1.1 + J1.2** (intégrité des migrations).

## Décisions du responsable (2026-07-28)

| Réf. | Décision | Conséquence |
|---|---|---|
| **D1** | Bases < v18 : perte assumée, **avec accord de l'utilisateur** | B-011 = détection + consentement + export de courtoisie + recréation. `fallbackToDestructiveMigration` reste proscrit |
| **D2** | **Les deux** : renommer l'existant *et* préparer un vrai service distant | B-110 (renommage seul, sans toucher à la logique) + B-111 (analyse Drive/Dropbox) + B-112 (implémentation) |
| **D3** | Démarrer par **J0**, puis **J1.1 + J1.2** | Ordre confirmé ci-dessous |
| **D4** | Le responsable dispose d'Android Studio et exécute builds/tests | Chaque livraison s'accompagne des **commandes exactes** à exécuter |
| **Prod** | **Aucun utilisateur en production** | J1 perd son caractère de sauvetage : c'est un chantier de qualité, pas d'urgence. Latitude pour refondre la chaîne de migrations |

---

## Jalon D — Environnement Docker *(Phase 6, livré le 2026-07-28)* ✅

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| D.1 | Créer `docker/` (image, compose, scripts) | B-130 | ✅ |
| D.2 | Documenter `.ai/DEV_ENVIRONMENT.md` | B-131 | ✅ |
| D.3 | Intégrer aux checklists et règles | B-132 | ✅ |
| D.4 | **Première exécution `make image && make verify`** | B-133 | ✅ |

---

## Vue d'ensemble des dépendances

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
J5 Découpage Repository / ViewModel ✅
      │
      ▼
J6 Fonctionnalités manquantes ⏳ (En cours)
      │
      ▼
J7 Industrialisation (CI, release)
```

---

## Jalon 5 — Découpage architectural ✅

**Objectif** : Découpage de `MainRepository` et `MainViewModel`.

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 5.1 | Extraire `SalesRepository` | B-050b | ✅ (c8dfdb6) |
| 5.2 | Extraire `StockRepository` (InventoryRepository) | B-050c | ✅ (563eee3) |
| 5.3 | Extraire `CustomerRepository` / `SupplierRepository` | B-050 | ✅ (Absorbés) |
| 5.4 | Extraire `CashRepository` (sessions, audits) | B-050 | ✅ (Absorbés) |
| 5.5 | Sortir la génération PDF/ticket du repository | B-051 | ✅ (a457eda) |
| 5.6 | Sortir les `Intent` Android vers `platform/` | B-052 | ✅ (056be5b) |
| 5.7 | `UiState` + ViewModel dédié pour `NewSaleScreen` | B-053, B-054 | ✅ (66c4285) |
| 5.8.1 | Extraction du domaine Inventaire (InventoryViewModel) | B-054 | ✅ (7c870b1) |
| 5.8.2 | Extraction du domaine Clients (CustomerViewModel) | B-054 | ✅ (61b616e) |
| 5.8.3 | Extraction du domaine Sessions (SessionViewModel) | B-054 | ✅ (b8ac697) |
| 5.8.4 | Extraction du domaine Rapports (ReportsViewModel) | B-054 | ✅ (a0eb83a) |
| 5.8.5 | Extraction du domaine Dépenses (ExpenseViewModel) | B-054 | ✅ (62ef8fa) |
| 5.8.6 | ViewModel pour domaines restants (Staff, Backup...) | B-054 | ✅ (2a86d11) |

---

## Jalon 6 — Fonctionnalités manquantes ⏳

**Séquençage J6** :
1.  **Phase 0** : Assainissement et Sécurité (Bugs bloquants) ✅ (e0ceb2c)
2.  **Phase 1** : Socle de Communication — i18n UI **J6.1A ✅ (82f05be)** ; Snackbars (6.5) encore ouverts
3.  **Phase 2** : Processus Métier — sessions **J6.2 ✅ (8658ab5)** ; permissions (6.3) encore ouvertes
4.  **Phase 3** : Monitoring & Surveillance
5.  **Phase 4** : Connectivité Cloud
6.  **Phase 5** : Refonte Structurelle (Monétaire)

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| **6.0** | **Phase 0 : Assainissement (BUG-009, 010, 012, 013)** | — | ✅ (e0ceb2c) |
| 6.1 | Écran de gestion du personnel (débloqué au J5.8.6) | B-070 | ✅ |
| 6.2 | Pilotage complet des sessions de caisse | B-071 | ✅ (8658ab5) |
| 6.3 | Refonte du parcours de permissions | B-078 | **NEXT / NOT STARTED** |
| 6.4 | Externalisation des textes UI + `values-en/` (**J6.1A**) | B-073, B-074 | ✅ (82f05be) |
| 6.5 | Snackbars cohérents | B-076 | □ |
| 6.6 | Alertes d'anomalie visibles dans l'UI (inc. BUG-008) | B-072 | □ |
| 6.7a | Rapport comparatif Google Drive vs Dropbox | B-111 | □ |
| 6.7b | Implémenter la sauvegarde automatique distante | B-112 | □ |
| 6.8 | Étude du passage à un type monétaire exact | B-077 | □ |

---

## Jalon 7 — Industrialisation □

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 7.1 | ktlint ou detekt | B-097 | □ |
| 7.2 | CI GitHub Actions (build + test + lint sur PR) | B-098 | □ |
| 7.3 | Couverture JaCoCo publiée | B-099 | □ |
| 7.4 | Tests Compose des parcours critiques | B-095 | □ |
| 7.5 | Configuration de signature et de release |  | □ |

---

### J6.1A — UI Internationalization : DONE

Commit technique `82f05be` (`82f05be50795cfe361eaee2c209a9451371291cd`), message `feat(j6): implement J6.1A UI internationalization`.

- 38 fichiers d'écrans, de composants UI et de ressources.
- 684 clés FR et 684 clés EN, parité complète, placeholders cohérents.
- Build SUCCESSFUL. 118/118 tests lors de la validation.
- Aucune modification de ViewModel, Repository, Manager, Gradle, Room/KSP ni de test.

### J6.2 — Sessions / clôture de caisse : DONE

Commit technique `8658ab59eaef16c9937b3e9e740813d15d06a42f`, message `feat(j6): implement J6.2 session management`. Parent `c0b2fda` (sync documentaire J6.1A). 9 fichiers techniques. Aucun fichier `.ai/` dans ce commit.

Comportement livré :
- une seule session active ; `openSession` refuse si une session existe déjà ;
- concurrence : `openSessionMutex` sur le `SalesRepository` `@Singleton`, contrôle + insertion dans `runInTransaction` ; le bouton d'ouverture ignore un second clic pendant `Processing` ;
- fond d'ouverture : vide / non numérique / négatif refusés ; `0` et `1500,5` acceptés (`SessionAmounts`) ;
- aperçu de la session active (`previewActiveSession` / `SessionPreview`) ;
- formule unique `expectedCash = openingBalance + sessionCash - sessionExpenses` (`expectedSessionCash`) ;
- clôture : comptage obligatoire ; `closingBalance = countedCash` ; `expectedBalance` figé avant toute écriture de manque ; écart = `closingBalance - expectedBalance` ;
- le manque (`addExpense`) n'est enregistré qu'après le retour de `closeSession`, pour ne pas fausser l'attendu ;
- vendeur prérempli via `getStaffName` si le personnel existe ; historique avec `label_session_gap`.

Validation : 128/128 tests, `compileDebugKotlin` OK, KSP OK, `assembleDebug` OK. I18n J6.1A conservée (684/684, missing 0, extra 0, placeholder 0). Aucun `UiText` / `uiText` / `uiTextOr` / `Context` pour traduire.

Le patch historique `j6.2-mixed-files-before-split.diff` (SHA-256 `61EBAE077D50F508B44B52ADA29E8C94AF4B26CEAEB5BC13B7C463E03B89FB11`) n'a pas été appliqué. J6.2 a été reconstruit sur le HEAD post-J6.1A. Un premier audit a exigé deux corrections (ordre du manque, concurrence) avant le GO FOR COMMIT.

J6.3, J6.4 (déjà J6.1A), J6.5–J6.8 et J7 restent dans leur état réel. Prochaine étape fonctionnelle : **6.3 permissions (B-078)**.

---

*Document mis à jour le 2026-09-26 (J6.2 TERMINÉ — commit 8658ab5). Mise à jour précédente : 2026-09-26 (J6.1A TERMINÉ — commit 82f05be).*
