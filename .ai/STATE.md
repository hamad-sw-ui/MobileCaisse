# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 82f05be50795cfe361eaee2c209a9451371291cd
- **HEAD actuel** : 82f05be50795cfe361eaee2c209a9451371291cd
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-09-26 (SUCCESS, validation J6.1A)
- **Dernière exécution des tests** : 2026-09-26 (118/118 SUCCESS, validation J6.1A)
- **KSP** : PASS
- **Working Tree** : CLEAN après le commit J6.1A (`82f05be`)
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
- **Dernière tâche terminée** : J6.1A — internationalisation UI FR/EN (TERMINÉE TECHNIQUEMENT — commit `82f05be50795cfe361eaee2c209a9451371291cd`).
- **Tâche en cours** : aucune implémentation. Synchronisation documentaire de cette clôture.
- **Prochaine tâche prévue** : J6.2 — sessions / clôture de caisse. **NEXT / NOT STARTED**. Le travail préparatoire séparé avant J6.1A n'est pas dans le code ; il reste dans le patch `j6.2-mixed-files-before-split.diff` (SHA-256 `61EBAE077D50F508B44B52ADA29E8C94AF4B26CEAEB5BC13B7C463E03B89FB11`).
- **Hors de ce commit** : snackbars (6.5 / B-076) toujours ouverts. Aucun ViewModel, Repository, Manager, Gradle, Room/KSP ni test modifié par J6.1A.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-008.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-007, BUG-009, BUG-010, BUG-011, BUG-012, BUG-013, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage J5 terminé : isolation totale des domaines fonctionnels.
    - Séquençage J6 validé : approche par phases (Bugs -> i18n -> Métier -> Monitoring -> Cloud -> Monétaire).
    - Scope Coroutines : Utilisation d'un `@ApplicationScope` injecté pour les traitements asynchrones de longue durée (SMS).
- **Risques connus** : 
    - Permissions contextuelles : l'UX doit être soignée pour éviter le blocage Android 14+.

## 🕒 Dernière Mise à jour
- **Date** : 2026-09-26
- **Agent** : AI-DOS — synchronisation documentaire après J6.1A
