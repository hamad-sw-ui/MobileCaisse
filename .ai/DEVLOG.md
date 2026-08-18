# 📓 JOURNAL DE DÉVELOPPEMENT (DEVLOG)

Ce journal retrace l\u0027historique des cycles de développement. Chaque entrée est ajoutée en fin de session.

---

## [2026-08-17] - J5.8.1 : Extraction de l\u0027InventoryViewModel

**Objectif** : Isoler le domaine Inventaire pour poursuivre l\u0027allégement du `MainViewModel`.

**Analyses réalisées** :
- Identification de 22 méthodes et 7 StateFlows liés au stock, audits, fournisseurs et arrivages.
- Analyse de la "Vente Rapide" du `StockScreen` : la logique appartient au domaine **SALES**, elle est donc conservée dans `MainViewModel` pour l\u0027instant.
- Vérification du rôle `isManager()` : conservé comme donnée de session globale.

**Décisions prises** :
- Création de `InventoryViewModel` et `InventoryUiState`.
- Migration chirurgicale des responsabilités Stock (CRUD, Alerts, Valuation), Audit, Supply et Supplier.
- Raccordement de 11 écrans, injectant parfois deux ViewModels (MAD approach).
- Création de 3 nouveaux tests unitaires pour garantir l\u0027intégrité de la logique extraite.

**Fichiers créés** :
- `InventoryViewModel.kt`
- `InventoryUiState.kt`
- `InventoryViewModelTest.kt`

**Fichiers modifiés** :
- `MainViewModel.kt` (Purge Inventory)
- 11 écrans de l\u0027UI (Raccordement)

**Résultats de compilation** : SUCCESS.
**Résultats des tests** : 82/82 SUCCESS. Commit final `7c870b1`.

---

## [2026-08-17] - J5.7 : Découpage structurel du ViewModel (NewSaleScreen)

**Objectif** : Extraire la logique de l\u0027écran de vente du `MainViewModel` monolithique.

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
