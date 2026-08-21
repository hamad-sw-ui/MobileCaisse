# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 62ef8faa98ab91a312ba09d877c905dd52ebc9ad
- **HEAD actuel** : 62ef8faa98ab91a312ba09d877c905dd52ebc9ad
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-19 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-19 (103/103 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel, ExpenseViewModel)
- **Dette technique** :
    - `MainViewModel` allégé (~480 lignes) mais encore responsable de l'Auth, Config et CRUD Boutique.
    - Consolidation Exports terminée : `ExportUtil` supprimé au profit de `ReportManager`.
    - Découpage architectural en cours (J5.8.5 terminé, J5.8.6 à suivre).

## 📋 Progression
- **Dernière tâche terminée** : J5.8.5 - Extraction ExpenseViewModel et consolidation Exports (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire.
- **Prochaine tâche prévue** : J5.8.6 - Finaliser l'assainissement (Paramètres, Staff...).

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage ViewModel : Chaque domaine fonctionnel doit posséder son propre ViewModel.
    - Centralisation CSV : Toute la logique de fichiers CSV/PDF est isolée dans `ReportManager` (Platform). Les ViewModels ne manipulent que les fichiers via `ShareManager`.
    - Sécurité Reports : Réactivité optimisée via `flatMapLatest` sur les bornes temporelles.
- **Risques connus** : 
    - Dette de test (Platform) : Les propriétés internes des Intent ne sont pas inspectées en JVM pure.
    - Multi-ViewModel dans l\u0027UI : Devenu le motif standard pour les écrans transverses.

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-19
- **Agent** : AI-DOS Independent Auditor
