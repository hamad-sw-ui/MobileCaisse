# 📓 JOURNAL DE DÉVELOPPEMENT (DEVLOG)

Ce journal retrace l\u0027historique des cycles de développement. Chaque entrée est ajoutée en fin de session.

---

## [2026-08-13] - B-050c : Extraction du cœur Stock (Commit 1)

**Objectif** : Isoler la logique de stock de `MainRepository` vers `InventoryRepository`.

**Analyses réalisées** :
- Identification des méthodes cœurs : `allStock`, `lowStockAlerts`, `getStockValuation`, `adjustStock`, `updateStock`.
- Analyse des transactions multi-DAOs (Stock + Expense + Movement).

**Décisions prises** :
- Injection de `AppDatabase` dans `InventoryRepository` pour conserver `runInTransaction`.
- Maintien temporaire de `deleteStock` dans `MainRepository` (dépendance `VenteDao`).
- Utilisation de proxys dans `MainRepository` pour éviter de casser le `MainViewModel` immédiatement.

**Fichiers modifiés** :
- `InventoryRepository.kt` (Création)
- `MainRepository.kt` (Refactor)
- `MainRepositoryTest.kt` (Setup)
- `SalesRepositoryTest.kt` (Setup)

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : 70/70 SUCCESS. Commit fonctionnel `8812490`.

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

**Analyses réalisées** :
- Diagnostic du verrouillage fichier via PowerShell et tests de manipulation directe.
- Identification d\u0027une faille structurelle dans `FakeAppDatabase`.

**Décisions prises** :
- Surcharge de `runInTransaction` pour bypasser le mécanisme Room en tests JVM.
- Levée du verrou par nettoyage Gradle forcé.

**Fichiers modifiés** :
- `app/src/test/java/com/reconsiliation/caisse/data/repository/FakeDaos.kt`

**Résultats de compilation** : SUCCESS (:app:assembleDebug).
**Résultats des tests** : Unit tests 65/65 SUCCESS (Niveau A).

---

## [2024-05-22] - J1.3 : Réécriture des migrations divergentes (B-010)

**Objectif** : Résoudre les incohérences entre les migrations Room et les entités Kotlin.

**Fichiers modifiés** :
- `app/src/main/java/com/reconsiliation/caisse/data/local/AppDatabase.kt`

**Résultats de compilation** : SUCCESS (KSP v29).
**Résultats des tests** : Unit tests 41/41 SUCCESS.
