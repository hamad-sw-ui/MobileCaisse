# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : de9637c8641c7bc2add6109d6026d8a6785e2329
- **HEAD actuel** : 2b59ade5801412c3d727d8e5f81da0fc0143b070
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-10-16 (SUCCESS, validation globale J7.5c : `compileDebugKotlin`, `kspDebugKotlin`, `:app:ktlintCheck`, `:app:testDebugUnitTest`, `:app:jacocoTestReport`, **`:app:assembleRelease`** — APK release 28.3 Mo généré et signé v2).
- **Dernière exécution des tests** : 2026-10-12 (193/193 JVM SUCCESS, `:app:testDebugUnitTest` + `:app:ktlintCheck` + `:app:jacocoTestReport` rapports HTML/XML générés avec succès).
- **KSP** : SUCCESS.
- **Ktlint** : Plugin `org.jlleitschuh.gradle.ktlint:14.2.0` intégré avec moteur `1.5.0` / ruleset `1.5.0` et `app/config/ktlint/baseline.xml` (117 fichiers couverts).
- **JaCoCo** : Plugin JaCoCo `0.8.12` intégré dans `app/build.gradle.kts`. Tâche `:app:jacocoTestReport` générant les rapports HTML/XML pour les 193 tests unitaires JVM avec exclusions ciblées (Hilt, Room, BuildConfig, R.class).
- **CI GitHub Actions** : Pipeline `.github/workflows/ci.yml` (JDK 17 Temurin, `gradle/actions/setup-gradle@v3`, `:app:ktlintCheck`, `:app:testDebugUnitTest`, `:app:assembleDebug`). Pipeline créé localement, committé (`f3c615e`) et validé statiquement ; aucun run distant n'a encore été exécuté faute de push (`push = 0`).
- **Jalon J7.5 — Configuration de signature et de release (CLÔTURÉ ✅)** :
  - **Lot 1 (`.gitignore`)** : Exclusions ciblées des magasins de clés (`*.jks`, `*.keystore`, `*.p12`, `*.pepk`) et propriétés de secrets (`keystore.properties`, `release.properties`) (`3bd5c5a` / `dcd5f6c`).
  - **Lot 2 (`app/build.gradle.kts`)** : Chargement conditionnel de `app/keystore.properties` avec validation Fail-Fast des 4 clés obligatoires (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) et création du gabarit versionné `app/keystore.properties.example` (`8f391bc` / `fda08e6`).
  - **Lot 3 (Validation locale APK Release)** : Génération locale d'un Keystore Développeur de Test non suivi (`app/release.jks`), assemblage `:app:assembleRelease` (APK 28.3 Mo) et vérification cryptographique `apksigner` validée (`Verifies: true`, schéma v2 `true`, `CN=MobileCaisse Developer Test, OU=Dev, O=MobileCaisse, C=FR`). Suite 193/193 JVM PASS validée (`2b59ade`). Zero secret committé dans Git.
- **Working Tree** : Modifications documentaires AI-DOS de clôture globale J7.5 en cours en working tree, **pas encore committées**.
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
    - Tests Compose UI J7.4 / B-095 (Couverture complète des 4 parcours critiques avec 22 tests Compose UI)
    - Configuration de Signature et Release J7.5 (Protection Git, signature conditionnelle Release, gabarit template, validation apksigner v2)
- **Dette technique & Réserves** :
    - Dette de style legacy isolée dans `app/config/ktlint/baseline.xml` (code métier et tests non reformates).
    - `MainViewModel` purgé des domaines isolés, responsable de l'Auth, Config et CRUD Boutique.
    - Réserve instrumentation legacy : 2 anciens tests d'instrumentation d'appareil (`DatabasePhysicalSecurityTest.kt` et `RoomMigrationTest.kt`) restent non compilables suite aux refactorings passés de repositories et dépendances Room ; maintenus hors périmètre du jalon J7.5.
    - Réserve d'exécution Android : L'exécution réelle des 22 tests Compose UI (`NumericKeypadTest.kt`, `PinScreenTest.kt`, `NewSaleScreenTest.kt`, `ClosureScreenTest.kt`) sur terminal/émulateur Android physique connecté reste non réalisée en environnement local headless sans émulateur.
    - Réserve Clé de Production : Le keystore utilisé pour la validation cryptographique J7.5c est un Keystore Développeur de Test local (`CN=MobileCaisse Developer Test...`). La clé de production finale et la publication Google Play restent à cadrer dans le Jalon 8.
    - B-039 : WorkManager reste planifié depuis `MainActivity` (hors scope J6.3).
    - Dettes feedback J6.5 (hors scope, non résolues) : SaleDetail / Settings-Splash (`uiError` non collecté à cause de la navigation immédiate) ; `convertErrorToOrphan` inutilisé ; `testPrint` silencieux ; overlay dialog Home.
    - Risques résiduels J6.7b : Recette Cloud end-to-end réelle sur appareil physique avec compte Google réel non exécutée ; TalkBack manuel non testé.

## 📋 Progression
- **Dernière tâche terminée** : J7.5 — Configuration de signature et de release (TERMINÉE / COMMITTÉE `8f391bc` / `fda08e6` / `2b59ade`).
- **Tâche en cours** : Jalon 8 — Production, Distribution et Déploiement (EN COURS — Cadrage & Préparation).
- **Prochaine tâche prévue** : Jalon 8 — Preparation de la release de production finale (Clé de production & Google Play Console).
- **Hors de ce commit technique** : Jalon 8+. Tous les jalons d'industrialisation J7.1 à J7.5 inclus sont 100% terminés.

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
    - J7.4 / B-095 : Couverture des 4 parcours critiques par 22 tests d'interface Compose UI (`NumericKeypadTest.kt`, `PinScreenTest.kt`, `NewSaleScreenTest.kt`, `ClosureScreenTest.kt`).
    - J7.5 : Industrialisation de la signature Release (Protection Git `.gitignore`, chargement conditionnel DSL `app/keystore.properties`, gabarit `keystore.properties.example`, validation cryptographique v2 par `apksigner`).

## 🕒 Dernière Mise à jour
- **Date** : 2026-10-16
- **Agent** : AI-DOS — enregistrement documentaire de clôture globale du jalon J7.5
