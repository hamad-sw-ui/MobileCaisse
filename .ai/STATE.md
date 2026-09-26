# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 8658ab59eaef16c9937b3e9e740813d15d06a42f
- **HEAD actuel** : 8658ab59eaef16c9937b3e9e740813d15d06a42f
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-09-26 (SUCCESS, `compileDebugKotlin` + `assembleDebug`, validation J6.2)
- **Dernière exécution des tests** : 2026-09-26 (128/128 SUCCESS, validation J6.2)
- **KSP** : PASS
- **Working Tree** : CLEAN après le commit technique J6.2 (`8658ab5`), avant cette synchronisation documentaire (non committée)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel, ExpenseViewModel, StaffViewModel, BackupViewModel, PrinterViewModel, CategoryViewModel, MaintenanceViewModel)
- **Dette technique** :
    - `MainViewModel` purégé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique.
    - BUG-008 (Date Anomaly) reste à traiter en tâche 6.6.

## 📋 Progression
- **Dernière tâche terminée** : J6.2 — pilotage des sessions de caisse (TERMINÉE TECHNIQUEMENT — commit `8658ab59eaef16c9937b3e9e740813d15d06a42f`).
- **Tâche en cours** : aucune implémentation. Synchronisation documentaire de cette clôture (pas encore committée).
- **Prochaine tâche prévue** : J6.3 — refonte du parcours de permissions (B-078). **NEXT / NOT STARTED**. Snackbars (6.5 / B-076) restent ouverts.
- **Hors de ce commit** : J6.3, J6.5, J6.6, J6.7, J6.8, J7. J6.1A (`82f05be`) reste le commit i18n UI ; J6.2 n'a pas touché aux ressources ni réintroduit `UiText`.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-008.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-007, BUG-009, BUG-010, BUG-011, BUG-012, BUG-013, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage J5 terminé : isolation totale des domaines fonctionnels.
    - Séquençage J6 validé : approche par phases (Bugs -> i18n -> Métier -> Monitoring -> Cloud -> Monétaire).
    - Scope Coroutines : Utilisation d'un `@ApplicationScope` injecté pour les traitements asynchrones de longue durée (SMS).
    - J6.2 : unicité de session au niveau applicatif (`SalesRepository` singleton + mutex + transaction). Pas de changement de schéma Room.
- **Risques connus** : 
    - Permissions contextuelles : l'UX doit être soignée pour éviter le blocage Android 14+.

## 🕒 Dernière Mise à jour
- **Date** : 2026-09-26
- **Agent** : AI-DOS — synchronisation documentaire après J6.2 (non committée)
