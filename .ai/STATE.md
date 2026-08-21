# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : a0eb83af6bc5a4ed569a2d1d5610c09bce243287
- **HEAD actuel** : a0eb83af6bc5a4ed569a2d1d5610c09bce243287
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-19 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-19 (96/96 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel)
- **Dette technique** :
    - `MainViewModel` allégé (~530 lignes) mais encore responsable des Dépenses et du CRUD Ventes/Boutique.
    - Duplication : `ClosureScreen` contient encore une fonction `exportToCsv` simplifiée (reporté à J5.8.5).
    - Découpage architectural en cours (J5.8.4 terminé, J5.8.5 à suivre).

## 📋 Progression
- **Dernière tâche terminée** : J5.8.4 - Extraction ReportsViewModel (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire.
- **Prochaine tâche prévue** : J5.8.5 - Extraction Dépenses (ExpensesViewModel) et consolidation Exports.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage ViewModel : Chaque domaine fonctionnel doit posséder son propre ViewModel.
    - Réactivité Reports : Utilisation de `flatMapLatest` sur les bornes temporelles pour éviter les fuites de collecteurs lors du changement de période (MONTH/YEAR).
    - Consolidation Exports : Centralisation de la logique CSV dans `ReportsViewModel` / `ExportUtil` (en cours de généralisation).
- **Risques connus** : 
    - Dette de test (Platform) : Les propriétés internes des Intent ne sont pas inspectées en JVM pure.
    - Multi-ViewModel dans l\u0027UI : Devenu le motif standard pour les écrans transverses (ex: ReportsScreen utilise Main, Inventory et Reports VM).

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-19
- **Agent** : AI-DOS Independent Auditor
