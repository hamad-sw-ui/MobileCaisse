# 📓 JOURNAL DE DÉVELOPPEMENT (DEVLOG)

Ce journal retrace l\u0027historique des cycles de développement. Chaque entrée est ajoutée en fin de session.

---

## [2026-08-17] - J5.7 : Découpage structurel du ViewModel (NewSaleScreen)

**Objectif** : Extraire la logique de l\u0027écran de vente du `MainViewModel` monolithique.

**Analyses réalisées** :
- Cartographie des dépendances du `NewSaleScreen`.
- Analyse du mécanisme de persistance `"cart_items"` dans le `SavedStateHandle`.
- Identification du couplage `userRole`.

**Décisions prises** :
- Création de `NewSaleViewModel` et `NewSaleUiState`.
- Transfert de la clé `"cart_items"` vers le nouveau ViewModel pour préserver la protection contre le process death.
- Transmission explicite du rôle utilisateur depuis l\u0027UI pour éviter la dépendance circulaire vers `MainViewModel`.
- Suppression de la fuite d\u0027Intent WhatsApp dans `NewSaleScreen` via `ShareManager`.
- Maintien de `addVenteWithItems` dans `MainViewModel` uniquement pour les "Ventes Rapides" du StockScreen (évite la duplication).

**Fichiers modifiés** :
- `NewSaleViewModel.kt` (Création)
- `NewSaleUiState.kt` (Création)
- `NewSaleViewModelTest.kt` (Création)
- `MainViewModel.kt` (Allégement du panier)
- `NewSaleScreen.kt` (Raccordement)
- `BigButton.kt` (Support enabled)

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : 79/79 SUCCESS. Commit final `66c4285`.

---

## [2026-08-17] - B-052 : Extraction des Intent et Share vers la couche Platform

**Objectif** : Purger les Repositories des dépendances de communication Android (Intents, FileProvider, Uri).

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : 75/75 SUCCESS. Commit final `056be5b`.

---

## [2026-08-16] - B-051 : Extraction PDF et Ticket vers la couche Platform

**Objectif** : Purger les Repositories des dépendances au Framework Android (PdfDocument, Bluetooth).

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : 75/75 SUCCESS. Commit final `a457eda`.

---

## [2026-08-13] - B-050c : Finalisation de InventoryRepository (Commit 2)

**Objectif** : Achever l\u0027extraction du domaine inventaire vers `InventoryRepository`.

**Résultats** : InventoryRepository complet, 75 tests SUCCESS. Commit `563eee3`.

---

## [2026-08-13] - B-050c : Extraction du cœur Stock (Commit 1)

**Objectif** : Isoler la logique de stock de `MainRepository` vers `InventoryRepository`.

**Résultats** : Cœur Stock extrait, atomicité préservée. Commit `8812490`.

---

## [2026-08-13] - B-050b : Extraction du domaine Sales

**Objectif** : Extraire tout le domaine des ventes vers `SalesRepository`.

**Résultats** : isolation réussie, raccordement du `SmsProcessor`, 6 nouveaux tests validés. Commit `c8dfdb6`.

---

## [2026-08-13] - Jalon 4 : Migration Hilt terminée

**Objectif** : Mettre en place l\u0027injection de dépendances sur l\u0027ensemble du projet.

**Résultats** : Plugin KSP configuré, `DatabaseModule` créé, ViewModels et Workers migrés. Commit final `de859ae`.

---

## [2024-05-22] - Résolution du blocage environnemental et Clôture J3 (B-094A)

**Objectif** : Lever le verrou sur `classes.jar` et valider les tests unitaires du Repository pour clôturer le Jalon 3.

**Fichiers modifiés** :
- `app/src/test/java/com/reconsiliation/caisse/data/repository/FakeDaos.kt`

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : Unit tests 65/65 SUCCESS.

---

## [2024-05-22] - J1.3 : Réécriture des migrations divergentes (B-010)

**Objectif** : Résoudre les incohérences entre les migrations Room et les entités Kotlin.

**Fichiers modifiés** :
- `app/src/main/java/com/reconsiliation/caisse/data/local/AppDatabase.kt`

**Résultats de compilation** : SUCCESS (KSP v29).
**Résultats des tests** : Unit tests 41/41 SUCCESS.
