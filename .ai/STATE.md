# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 056be5bd042a5ad721c768672c5474075aa7036c
- **HEAD actuel** : 056be5bd042a5ad721c768672c5474075aa7036c
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-17 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-17 (75/75 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
- **Dette technique** :
    - `MainViewModel` (~750 lignes) centralise encore la logique de tous les écrans.
    - Orchestration monolithique du ViewModel à découper (J5.7/J5.8).

## 📋 Progression
- **Dernière tâche terminée** : B-052 - Extraction Intents/Share (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire.
- **Prochaine tâche prévue** : B-053/B-054 - Découpage structurel du ViewModel.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Isolation des Repositories : Les Repositories ne doivent plus dépendre de `Context`, `Intent`, `File`, `PdfDocument` ou `Bluetooth`.
    - Orchestration par le ViewModel : Le ViewModel coordonne les données (Repositories) et les effets système (Platform Managers).
- **Risques connus** : 
    - Dette de test (Platform) : Les propriétés internes des Intent ne sont pas inspectées en JVM pure.

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-17
- **Agent** : AI-DOS Independent Auditor
