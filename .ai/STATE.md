# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : de9637c8641c7bc2add6109d6026d8a6785e2329
- **HEAD actuel** : 509a1d0675aa14d70e4388053fdcbd9d12f68e8d
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-10-05 (SUCCESS, validation J7.1a LOT B-097 : `compileDebugKotlin`, `kspDebugKotlin`, `:app:ktlintCheck`, **`assembleDebug`** — APK debug généré).
- **Dernière exécution des tests** : 2026-10-05 (193/193 SUCCESS, `:app:testDebugUnitTest` + `:app:ktlintCheck` 9/9 tâches d'analyse validées avec baseline, validation J7.1a)
- **KSP** : SUCCESS.
- **Ktlint** : Plugin `org.jlleitschuh.gradle.ktlint:14.2.0` intégré avec moteur `1.5.0` / ruleset `1.5.0` et `app/config/ktlint/baseline.xml` (117 fichiers couverst).
- **Working Tree** : Modifications documentaires AI-DOS en cours en working tree (enregistrement J7.1a / B-097), **pas encore committées**.
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude + timestamp SMS)
    - Repositories (Data pure : Sales, Inventory, Main, RemoteBackup)
    - Platform (Services Framework : ReportManager, PrinterManager, ShareManager)
    - ViewModels (Orchestration : MainViewModel, NewSaleViewModel, InventoryViewModel, CustomerViewModel, SessionViewModel, ReportsViewModel, ExpenseViewModel, StaffViewModel, BackupViewModel, PrinterViewModel, CategoryViewModel, MaintenanceViewModel)
    - Permissions (logique JVM : `PermissionPolicy` / `SmsSyncOutcome` / `SmsCatchUpState`)
    - Feedback UI J6.5 (Snackbars locales)
    - Alertes et filtrage d'anomalies J6.6 (Timestamp SMS réel, contrat `AnomalyResult` + rollback, filtre `ANOMALIES` ActionLogScreen, 10 tests de logique)
    - Remote Backup J6.7b (Client REST Google Drive, Repository, Worker, Rotation, WorkManager Startup, Settings UI, Restauration Cloud UI)
    - Outillage Qualité J7.1a (Intégration `ktlint` Gradle plugin v14.2.0 + moteur v1.5.0 + baseline 117 fichiers)
- **Dette technique** :
    - Dette de style legacy isolée dans `app/config/ktlint/baseline.xml` (code métier et tests non reformates).
    - `MainViewModel` purgé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique.
    - B-039 : WorkManager reste planifié depuis `MainActivity` (hors scope J6.3).
    - Dettes feedback J6.5 (hors scope, non résolues) : SaleDetail / Settings-Splash (`uiError` non collecté à cause de la navigation immédiate) ; `convertErrorToOrphan` inutilisé ; `testPrint` silencieux ; overlay dialog Home.
    - Risques résiduels J6.7b : Recette Cloud end-to-end réelle sur appareil physique avec compte Google réel non exécutée ; TalkBack manuel non testé.

## 📋 Progression
- **Dernière tâche terminée** : J7.1a / B-097 — Intégration ktlint Gradle Plugin & Baseline (TERMINÉE / COMMITTÉE `509a1d0`).
- **Tâche en cours** : aucune implémentation. Synchronisation documentaire AI-DOS J7.1a / B-097.
- **Prochaine tâche prévue** : J7.2 / B-098 — CI GitHub Actions (Non commencé / CI non créée).
- **Hors de ce commit technique** : J7.2 (CI). Tous les jalons antérieurs jusqu'à J7.1a sont terminés.

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
    - J6.6 : timestamp SMS réel (`IncomingSms.timestampMillis`), traçabilité hors transaction des anomalies de blocage (`CRITICAL`), filtre `ANOMALIES` strict dans `ActionLogScreen`.
    - J6.7b : Sauvegarde distante automatique Google Drive via REST API v3, isolation du secret dérivé (`"${managerCode.trim()}a1A!"`), rotation sur 7 jours civils + 4 mois, WorkManager `@HiltWorker`, UI Google Drive dans `SettingsScreen`, Restauration Cloud dans `RestorationWizardScreen` avec réutilisation 100% du pipeline d'importation existant.
    - J6.8 / B-077 : Maintien du stockage monétaire SQLite existant en `Double` (pas de migration physique DB vers `Long` ou `BigDecimal` à ce stade). Renforcement des arrondis explicites aux points de calculs métier. Value Class domaine `Money(val amount: Double)` utilisable sans modification DB. Toute migration future nécessitera un chantier dédié et une nouvelle décision.

## 🕒 Dernière Mise à jour
- **Date** : 2026-10-05
- **Agent** : AI-DOS — enregistrement documentaire de la décision humaine J6.8 / B-077 Lot 1
