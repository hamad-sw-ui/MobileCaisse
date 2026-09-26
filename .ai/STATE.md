# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 52b4090917c02df6d83bde2f4beeab8657e361e2
- **HEAD actuel** : 52b4090917c02df6d83bde2f4beeab8657e361e2
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-09-26 (SUCCESS, `compileDebugKotlin` seulement — validation J6.3). `assembleDebug` **n'a pas été exécuté** pour J6.3.
- **Dernière exécution des tests** : 2026-09-26 (141/141 SUCCESS, `:app:testDebugUnitTest`, validation J6.3)
- **KSP** : warnings Room préexistants uniquement (non bloquants)
- **Working Tree** : CLEAN après le commit technique J6.3 (`52b4090`), avant cette synchronisation documentaire (non committée)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel, ExpenseViewModel, StaffViewModel, BackupViewModel, PrinterViewModel, CategoryViewModel, MaintenanceViewModel)
    - Permissions (logique JVM : `PermissionPolicy` / `SmsSyncOutcome` / `SmsCatchUpState`)
- **Dette technique** :
    - `MainViewModel` purégé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique.
    - BUG-008 (Date Anomaly) reste à traiter en tâche 6.6.
    - B-039 : WorkManager reste planifié depuis `MainActivity` (hors scope J6.3).

## 📋 Progression
- **Dernière tâche terminée** : J6.3 — parcours de permissions / B-078 / BUG-010 (TERMINÉE TECHNIQUEMENT — commit `52b4090917c02df6d83bde2f4beeab8657e361e2`).
- **Tâche en cours** : aucune implémentation. Synchronisation documentaire de cette clôture (pas encore committée).
- **Prochaine tâche prévue** : J6.5 — snackbars cohérents (B-076). **NEXT / NOT STARTED**.
- **Hors de ce commit** : J6.5, J6.6, J6.7, J6.8, J7. J6.1A (`82f05be`) et J6.4 (même livrable i18n) restent terminés. J6.2 (`8658ab5`) reste intact.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-008.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-007, BUG-009, BUG-010, BUG-011, BUG-012, BUG-013, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage J5 terminé : isolation totale des domaines fonctionnels.
    - Séquençage J6 validé : approche par phases (Bugs -> i18n -> Métier -> Monitoring -> Cloud -> Monétaire).
    - Scope Coroutines : Utilisation d'un `@ApplicationScope` injecté pour les traitements asynchrones de longue durée (SMS).
    - J6.2 : unicité de session au niveau applicatif (`SalesRepository` singleton + mutex + transaction). Pas de changement de schéma Room.
    - J6.3 : décisions runtime dans l'UI / Activity ; `PermissionPolicy` testable sans Android ; les Repositories ne demandent pas de permissions.
- **Risques connus / résidus J6.3 (non clos)** : 
    - `BLUETOOTH_SCAN` déclaré, jamais demandé ni utilisé.
    - `POST_NOTIFICATIONS` non redemandée aux utilisateurs déjà onboardés.
    - Bannière `READ_SMS` non mise à jour automatiquement après retour Paramètres.
    - Tests permission uniquement JVM (pas instrumentés).
    - B-039 (WorkManager hors de `MainActivity`) hors scope.

## 🕒 Dernière Mise à jour
- **Date** : 2026-09-26
- **Agent** : AI-DOS — synchronisation documentaire après J6.3 (non committée)
