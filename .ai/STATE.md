# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 66c42855db053880f58349127e37e781c193b03c
- **HEAD actuel** : 66c42855db053880f58349127e37e781c193b03c
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-17 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-17 (79/79 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel)
- **Dette technique** :
    - `MainViewModel` monolithique en cours de découpage (J5.7 terminé, J5.8 à suivre).
    - `MainViewModel` encore à ~660 lignes après extraction `NewSale`.

## 📋 Progression
- **Dernière tâche terminée** : J5.7 - Extraction NewSaleViewModel (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire.
- **Prochaine tâche prévue** : J5.8 - Étendre le découpage architectural aux écrans restants.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage ViewModel : Chaque écran complexe doit posséder son propre ViewModel et son `UiState`.
    - Isolation des Repositories : Confirmée et maintenue.
    - Orchestration par le ViewModel : Le ViewModel coordonne les données et les effets système via les Platform Managers.
- **Risques connus** : 
    - Dette de test (Platform) : Les propriétés internes des Intent ne sont pas inspectées en JVM pure.
    - Persistance du panier : Repose sur `SavedStateHandle` dans `NewSaleViewModel`.

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-17
- **Agent** : AI-DOS Independent Auditor
