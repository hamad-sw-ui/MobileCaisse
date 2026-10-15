# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : de9637c8641c7bc2add6109d6026d8a6785e2329
- **HEAD actuel** : 81683bb07c789dde7600e996c0f3f825ad9e1884
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-10-14 (SUCCESS, validation J7.4 LOT 4 B-095 : `compileDebugKotlin`, `kspDebugKotlin`, `:app:ktlintCheck`, `:app:testDebugUnitTest`, `:app:jacocoTestReport`, **`assembleDebug`** — APK debug généré).
- **Dernière exécution des tests** : 2026-10-12 (193/193 JVM SUCCESS, `:app:testDebugUnitTest` + `:app:ktlintCheck` + `:app:jacocoTestReport` rapports HTML/XML générés avec succès).
- **KSP** : SUCCESS.
- **Ktlint** : Plugin `org.jlleitschuh.gradle.ktlint:14.2.0` intégré avec moteur `1.5.0` / ruleset `1.5.0` et `app/config/ktlint/baseline.xml` (117 fichiers couverts).
- **JaCoCo** : Plugin JaCoCo `0.8.12` intégré dans `app/build.gradle.kts`. Tâche `:app:jacocoTestReport` générant les rapports HTML/XML pour les 193 tests unitaires JVM avec exclusions ciblées (Hilt, Room, BuildConfig, R.class).
- **CI GitHub Actions** : Pipeline `.github/workflows/ci.yml` (JDK 17 Temurin, `gradle/actions/setup-gradle@v3`, `:app:ktlintCheck`, `:app:testDebugUnitTest`, `:app:assembleDebug`). Pipeline créé localement, committé (`f3c615e`) et validé statiquement ; aucun run distant n'a encore été exécuté faute de push (`push = 0`).
- **Tests UI Compose J7.4 Lot 1** : Création du test d'interface Compose `app/src/androidTest/java/com/reconsiliation/caisse/ui/components/NumericKeypadTest.kt` (+75 insertions). Validation des interactions (rendu 0..9/suppression, callbacks numériques, callback d'effacement). 193/193 tests JVM verts (`:app:testDebugUnitTest`). Commit technique `233f9c6258cd8b732384287a837c3580b63002e9` validé.
- **Tests UI Compose J7.4 Lot 2** : Extraction du composable stateless `PinContent` et de la fonction partagée de production `handlePinKeyPress(currentPin, key, maxDigits)` dans `PinScreen.kt` (+96 / -29). Création du test d'interface Compose `app/src/androidTest/java/com/reconsiliation/caisse/ui/screens/PinScreenTest.kt` (+208). 7 méthodes de test couvrant l'interface initiale, les tags `pin_dot_0`..`3`, la saisie, l'effacement, l'affichage de `pin_invalid`, la logique `handlePinKeyPress` et le test d'intégration UI de plafonnement à 4 chiffres. 193/193 tests JVM verts. Commit technique `8291f8ec720d3e6f56caaed9d3606c7241d7eb47` validé.
- **Tests UI Compose J7.4 Lot 3** : Extraction du composable stateless `NewSaleContent` dans `NewSaleScreen.kt` (+201 / -141). Création du test d'interface Compose `app/src/androidTest/java/com/reconsiliation/caisse/ui/screens/NewSaleScreenTest.kt` (+221). 5 méthodes de test couvrant l'état initial du panier vide, l'affichage des articles dans le panier, l'ajustement des quantités (+/-), la suppression d'un article et le bouton de validation de vente. 193/193 tests JVM verts. Commit technique `4c5d6777f2d6fdec915d95abb9cf56ed41910a70` validé.
- **Tests UI Compose J7.4 Lot 4** : Extraction du composable stateless `ClosureContent` dans `ClosureScreen.kt` (+98 / -41). Création du test d'interface Compose `app/src/androidTest/java/com/reconsiliation/caisse/ui/screens/ClosureScreenTest.kt` (+272). 7 méthodes de test couvrant la vue d'absence d'activité, le récapitulatif de clôture, l'exportation CSV, la sauvegarde totale, la carte de statut fermé, la soumission de clôture avec comptage d'espèces et l'affichage de l'écart de caisse formaté. 193/193 tests JVM verts. Commit technique `81683bb07c789dde7600e996c0f3f825ad9e1884` validé par audits Red Team (`GO SOUS RÉSERVES`).
- **Working Tree** : Modifications documentaires AI-DOS en cours en working tree (enregistrement J7.4 Lot 4 / B-095), **pas encore committées**.
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
    - Integration Continue J7.2 / B-098 (Workflow GitHub Actions `.github/workflows/ci.yml`)
    - Couverture de Code JaCoCo J7.3 / B-099 (Plugin 0.8.12, tâche `:app:jacocoTestReport`, rapports HTML/XML)
    - Tests Compose UI J7.4 Lot 1 / B-095 (Test isolé de composant UI `NumericKeypadTest.kt`)
    - Tests Compose UI J7.4 Lot 2 / B-095 (Saisie PIN & validation `PinScreenTest.kt`)
    - Tests Compose UI J7.4 Lot 3 / B-095 (Nouvelle vente & panier `NewSaleScreenTest.kt`)
    - Tests Compose UI J7.4 Lot 4 / B-095 (Clôture de caisse & récapitulatif `ClosureScreenTest.kt`)
- **Dette technique & Réserves** :
    - Dette de style legacy isolée dans `app/config/ktlint/baseline.xml` (code métier et tests non reformates).
    - `MainViewModel` purgé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique.
    - Réserve instrumentation legacy : 2 anciens tests d'instrumentation d'appareil (`DatabasePhysicalSecurityTest.kt` et `RoomMigrationTest.kt`) restent non compilables suite aux refactorings passés de repositories et dépendances Room ; maintenus hors périmètre du Lot 4.
    - Réserve d'exécution Android : L'exécution réelle des suites Compose UI (`NumericKeypadTest.kt`, `PinScreenTest.kt`, `NewSaleScreenTest.kt`, `ClosureScreenTest.kt`) sur terminal/émulateur Android physique connecté reste non réalisée en environnement local headless sans émulateur.
    - B-039 : WorkManager reste planifié depuis `MainActivity` (hors scope J6.3).
    - Dettes feedback J6.5 (hors scope, non résolues) : SaleDetail / Settings-Splash (`uiError` non collecté à cause de la navigation immédiate) ; `convertErrorToOrphan` inutilisé ; `testPrint` silencieux ; overlay dialog Home.
    - Risques résiduels J6.7b : Recette Cloud end-to-end réelle sur appareil physique avec compte Google réel non exécutée ; TalkBack manuel non testé.

## 📋 Progression
- **Dernière tâche terminée** : J7.4 / B-095 Lot 4 — Tests Compose UI ClosureScreen (TERMINÉE / COMMITTÉE `81683bb`).
- **Tâche en cours** : J7.4 / B-095 — Tests Compose des parcours critiques (EN COURS — Lots 1, 2, 3 & 4 validés). Synchronisation documentaire AI-DOS Lot 4.
- **Prochaine tâche prévue** : Synthèse globale du jalon J7.4 (B-095) et passage au jalon J7.5 Signature & Release.
- **Hors de ce commit technique** : J7.5 et jalons ultérieurs. Tous les lots techniques J7.4 Lots 1 à 4 sont terminés.

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
    - J7.1a / B-097 : Outillage qualité Ktlint plugin v14.2.0 + moteur v1.5.0 + baseline 117 fichiers (`app/config/ktlint/baseline.xml`).
    - J7.2 / B-098 : Pipeline CI GitHub Actions minimal et autonome dans `.github/workflows/ci.yml` (JDK 17, `setup-gradle@v3`, `:app:ktlintCheck`, `:app:testDebugUnitTest`, `:app:assembleDebug`).
    - J7.3 / B-099 : Intégration JaCoCo 0.8.12 pour la mesure de couverture des tests unitaires JVM (`:app:jacocoTestReport`) avec exclusions ciblées du code généré (Hilt, Room, BuildConfig, R.class).
    - J7.4 / B-095 Lot 1 : Implémentation du test d'interface Compose `NumericKeypadTest.kt` sous `app/src/androidTest/java/com/reconsiliation/caisse/ui/components/` sans modification de code applicatif ni dépendance Gradle supplémentaire.
    - J7.4 / B-095 Lot 2 : Refactoring de `PinScreen.kt` avec extraction de `PinContent` et de `handlePinKeyPress(currentPin, key, maxDigits)`. Création de `PinScreenTest.kt` (+208) avec 7 tests d'interface et d'état.
    - J7.4 / B-095 Lot 3 : Refactoring de `NewSaleScreen.kt` avec extraction de `NewSaleContent` (+201 / -141). Création de `NewSaleScreenTest.kt` (+221) avec 5 tests d'interface Compose.
    - J7.4 / B-095 Lot 4 : Refactoring de `ClosureScreen.kt` avec extraction de `ClosureContent` (+98 / -41). Création de `ClosureScreenTest.kt` (+272) avec 7 tests d'interface Compose (absence d'activité, récapitulatif, exportation CSV, sauvegarde totale, carte de statut fermé, soumission de clôture avec comptage d'espèces, calcul d'écart de caisse).

## 🕒 Dernière Mise à jour
- **Date** : 2026-10-14
- **Agent** : AI-DOS — enregistrement documentaire du lot J7.4 Lot 4 / B-095
