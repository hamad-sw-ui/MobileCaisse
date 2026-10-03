# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 557524168574ebce25f0e0676c1eaab797c6e06c
- **HEAD actuel** : 557524168574ebce25f0e0676c1eaab797c6e06c
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-10-03 (SUCCESS, validation J6.7b LOT 3C-1 : `compileDebugKotlin`, `kspDebugKotlin`, **`assembleDebug`** — APK debug généré).
- **Dernière exécution des tests** : 2026-10-03 (193/193 SUCCESS, `:app:testDebugUnitTest`, validation J6.7b LOT 3C-1)
- **KSP** : SUCCESS. Warnings Room préexistants uniquement (non bloquants)
- **Working Tree** : CLEAN après le commit technique J6.7b LOT 3C-1 (`5575241`). Cette synchronisation documentaire est en working tree, **pas encore committée**.
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude + timestamp SMS)
    - Repositories (Data pure : Sales, Inventory, Main)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel, ExpenseViewModel, StaffViewModel, BackupViewModel, PrinterViewModel, CategoryViewModel, MaintenanceViewModel)
    - Permissions (logique JVM : `PermissionPolicy` / `SmsSyncOutcome` / `SmsCatchUpState`)
    - Feedback UI J6.5 (Snackbars locales)
    - Alertes et filtrage d'anomalies J6.6 (Timestamp SMS réel, contrat `AnomalyResult` + rollback, filtre `ANOMALIES` ActionLogScreen, 10 tests de logique)
- **Dette technique** :
    - `MainViewModel` purgé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique. **Non refactoré en J6.6.**
    - B-039 : WorkManager reste planifié depuis `MainActivity` (hors scope J6.3).
    - Dettes feedback J6.5 (hors scope, non résolues) : SaleDetail / Settings-Splash (`uiError` non collecté à cause de la navigation immédiate) ; `convertErrorToOrphan` inutilisé ; `testPrint` silencieux ; overlay dialog Home.

## 📋 Progression
- **Dernière tâche terminée** : J6.7b LOT 3C-1 — Restauration Cloud (Fondations) (TERMINÉE TECHNIQUEMENT — commit `557524168574ebce25f0e0676c1eaab797c6e06c`).
- **Tâche en cours** : aucune implémentation. Synchronisation documentaire AI-DOS J6.7b LOT 3C-1.
- **Prochaine tâche prévue** : jalon fonctionnel **J6.7b LOT 3C-2** — Restauration Cloud UI dans `RestorationWizardScreen` & Recette Globale (B-112-LOT3C2). **NEXT / NOT STARTED**.
- **Hors de ce commit technique** : J6.7b LOT 3C-2, J6.8, J7. J6.1A (`82f05be`), J6.2 (`8658ab5`), J6.3 (`52b4090`), J6.5 (`498b9ba`), J6.6 (`a34e5f3`), J6.7b LOT 1 (`8bac07c`), J6.7b LOT 2 (`11d47cb`), J6.7b LOT 3A (`55067de`) et J6.7b LOT 3B (`0a8c13b`) restent terminés et intacts.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-007, BUG-008, BUG-009, BUG-010, BUG-011, BUG-012, BUG-013, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - Découpage J5 terminé : isolation totale des domaines fonctionnels.
    - Séquençage J6 validé : approche par phases (Bugs -> i18n -> Métier -> Monitoring -> Cloud -> Monétaire).
    - Scope Coroutines : Utilisation d'un `@ApplicationScope` injecté pour les traitements asynchrones de longue durée (SMS).
    - J6.2 : unicité de session au niveau applicatif (`SalesRepository` singleton + mutex + transaction). Pas de changement de schéma Room.
    - J6.3 : décisions runtime dans l'UI / Activity ; `PermissionPolicy` testable sans Android ; les Repositories ne demandent pas de permissions.
    - J6.5 : feedback **local à l'écran** (`SnackbarHostState` + `LaunchedEffect` + clear). Pas de bus global, pas de `UiText`, pas de `Context` dans les ViewModels. `MainViewModel` non refactoré.
    - J6.6 : timestamp SMS réel (`IncomingSms.timestampMillis`), traçabilité hors transaction des anomalies de blocage (`CRITICAL`), filtre `ANOMALIES` strict dans `ActionLogScreen`, pas de bus d'événements global.
- **Risques connus / résidus J6.3 (non clos)** : 
    - `BLUETOOTH_SCAN` déclaré, jamais demandé ni utilisé.
    - `POST_NOTIFICATIONS` non redemandée aux utilisateurs déjà onboardés.
    - Bannière `READ_SMS` non mise à jour automatiquement après retour Paramètres.
    - Tests permission uniquement JVM (pas instrumentés).
    - B-039 (WorkManager hors de `MainActivity`) hors scope.
- **Résidus J6.5 (non clos, hors scope assumé)** :
    - Erreurs silencieuses sur écrans qui `popBackStack` / naviguent immédiatement (SaleDetail, Settings/Splash).
    - `convertErrorToOrphan` et `testPrint` sans canal de feedback.
    - Overlay possible dialog / Snackbar à l'ouverture de session (Home).

## 🕒 Dernière Mise à jour
- **Date** : 2026-09-27
- **Agent** : AI-DOS — synchronisation documentaire après J6.6 (non committée)
