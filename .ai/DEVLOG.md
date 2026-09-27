# # 🪵 JOURNAL DE BORD (DEVLOG)

## 🗓️ 2026-09-27 — J6.6 alertes d'anomalies / timestamp SMS / B-072 / BUG-008
### Actions réalisées :
- **J6.6 terminé techniquement** : commit `a34e5f312802d9bbc2b06685f338de032a96bb9c` (`feat(j6): implement J6.6 anomaly detection, sms timestamping and audit log filters`). Parent `0fdbe02b5ee551250f1ce1de01b91ddd7c13a000`. 14 fichiers, +381 / −23. Aucun `.ai/` dans ce commit.
- **Architecture retenue** :
  - **LOT 1** (5) : Timestamp SMS réel (`IncomingSms.timestampMillis`). LIVE (`SmsMessage`), INBOX (`Telephony.Sms.Inbox.DATE`), MANUAL (`null`). Détection `AnomalyEngine.isSmsClockAnomaly` avec seuil `> 30 min`. Résolution de BUG-008 / B-029.
  - **LOT 2** (4) : Contrat `AnomalyResult(blocked, message, severity, code)`. Codes `FUTURE`, `DUPLICATE`, `HIGH_AMOUNT`. Rollback SQLite des ventes bloquées dans la transaction, puis journalisation `ANOMALY_DETECTED` `CRITICAL` hors transaction. Avertissements `HIGH_AMOUNT` (`WARNING`) commités dans la transaction.
  - **LOT 3** (5) : Filtre `ANOMALIES` strict (`DATE_ANOMALY` / `ANOMALY_DETECTED`) dans `ActionLogScreen`. i18n FR/EN. Fallback `details`. Snackbar `NewSaleScreen`.
- **Preuve** : 169/169 tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` / KSP / **`assembleDebug` SUCCESS**.
- **Audits** : contrôles pré-commit et post-commit **VALIDÉS**.
- **Séparation** : commit technique `a34e5f3` d'abord. Cette synchronisation AI-DOS est un working tree distinct, **pas encore committée**. **J6.7 non commencé.**

## 🗓️ 2026-09-26 — J6.5 snackbars / feedback UI / B-076
### Actions réalisées :
- **J6.5 terminé techniquement** : commit `498b9ba90509122b7e98d59c96879b0b933889bf` (`feat(j6): implement J6.5 snackbar and feedback handling`). Parent `d0e608e`. 25 fichiers, +264 / −38. Aucun `.ai/` dans ce commit.
- **Architecture retenue** : Snackbar Material 3 **locale** (`SnackbarHostState` + `LaunchedEffect` + `clearUiState` / `clearError`). Pas de bus global, pas de `SharedFlow` / `Channel` / `UiText` / `SnackbarManager`. ViewModels sans `Context`. `MainViewModel` non refactoré.
- **LOT 1 (19)** : Customer, Debt, Expense, Reports (`@StringRes`), Staff (`onResult`, plus de faux succès), NewSale (`clearUiState()` = Idle seulement) + 5 tests + clé `error_staff_deactivate`.
- **LOT 2 (2)** : Home / Closure — `SessionUiState.Error` → Snackbar → `clearUiState()`. J6.2 intact.
- **LOT 3 (4)** : SalesList, Stock, SmsError, Support — `uiError` consommé. Succès optimiste `sale_deleted` retiré.
- **Correction Stock** : `addVenteWithItems()` retourne un `Job` ; `join()` puis succès seulement si `uiError.value == null`. Empêche succès puis erreur.
- **Preuve** : 147/147 tests JVM (`--rerun-tasks`), `compileDebugKotlin` / KSP / **`assembleDebug` SUCCESS**. I18n 700/700. J6.2 et J6.3 hors diff.
- **Audits** : contrôle final pré-commit GO ; post-commit **VALIDÉ**.
- **Séparation** : commit technique `498b9ba` d'abord. Cette synchronisation AI-DOS est un working tree distinct, **pas encore committée**. **J6.6 / LOT 4 non commencé.**

## 🗓️ 2026-09-26 — J6.3 permissions Android / BUG-010
### Actions réalisées :
- **J6.3 terminé techniquement** : commit `52b4090917c02df6d83bde2f4beeab8657e361e2` (`feat(j6): implement J6.3 permission handling`). Parent `082f2e0`. 16 fichiers techniques. Aucun `.ai/` dans ce commit.
- **BUG-010 / B-078** : plus de demande groupée au lancement. Demandes unitaires au point d'usage, rationale, refus simple / définitif, Paramètres, relecture au `ON_RESUME`.
- **Protections** : `SmsSyncManager` capture `SecurityException` ; CameraX seulement si `CAMERA` accordée ; `BLUETOOTH_CONNECT` vérifié avant `bondedDevices` / `connect` ; `NotificationHelper.deliver` ignore le refus.
- **Preuve** : 141 tests JVM, `compileDebugKotlin` OK, `testDebugUnitTest` OK. **`assembleDebug` non exécuté.** I18n 699/699. J6.2 intact. Aucun `UiText`.
- **Audits** : pré-commit GO FOR COMMIT J6.3 ; post-commit GO FOR AI-DOS SYNCHRONIZATION J6.3.
- **Séparation** : commit technique `52b4090` d'abord. Cette synchronisation AI-DOS est un working tree distinct, **pas encore committée**.

## 🗓️ 2026-09-26 — J6.2 sessions / clôture de caisse
### Actions réalisées :
- **J6.2 terminé techniquement** : commit `8658ab59eaef16c9937b3e9e740813d15d06a42f` (`feat(j6): implement J6.2 session management`). Parent `c0b2fda`. 9 fichiers techniques. Aucun `.ai/` dans ce commit.
- **Pourquoi ce n'est pas le patch historique** : `j6.2-mixed-files-before-split.diff` mélangeait le métier et `UiText`. Il a servi de source de comportement seulement. Reconstruction sur le HEAD post-J6.1A, `stringResource` conservé.
- **Premier audit** : NO-GO. (1) `addExpense` du manque avant `closeSession` pouvait baisser `expectedBalance`. (2) `getActiveSession` puis insert n'était pas atomique ; le bouton n'était pas protégé.
- **Corrections** : manque uniquement dans `afterClose`, après retour de `closeSession` ; `openSessionMutex` + `runInTransaction` ; `Processing` posé avant `launch` ; bouton désactivé.
- **Second audit** : GO FOR COMMIT J6.2.
- **Preuve** : 128/128 tests (dont manque après clôture et 8 ouvertures concurrentes). `compileDebugKotlin` / KSP / `assembleDebug` OK. I18n 684/684. Aucun `UiText`.
- **Séparation** : commit technique `8658ab5` d'abord. Cette synchronisation AI-DOS est un working tree distinct, **pas encore committée**.

## 🗓️ 2026-09-26 — J6.1A internationalisation UI
### Actions réalisées :
- **J6.1A terminé techniquement** : commit `82f05be50795cfe361eaee2c209a9451371291cd` (`feat(j6): implement J6.1A UI internationalization`).
- **Périmètre** : 38 fichiers d'UI et de ressources. 684 clés FR, 684 clés EN, parité complète, placeholders cohérents.
- **Preuve** : Build SUCCESSFUL, 118/118 tests. Aucun ViewModel, Repository, Manager, Gradle, Room/KSP ni test modifié.
- **J6.2** : non commencé dans le code. Travail préparatoire séparé avant ce commit, conservé hors dépôt dans `j6.2-mixed-files-before-split.diff` (SHA-256 `61EBAE077D50F508B44B52ADA29E8C94AF4B26CEAEB5BC13B7C463E03B89FB11`). Non intégré.
- **Working Tree** : propre après le commit.

## 🗓️ 2026-08-19 — Extraction J5.8.5 (ExpenseViewModel)
### Actions réalisées :
- **Extraction Domaine Dépenses** : Création de `ExpenseViewModel` gérant `allExpenses` et les catégories `"EXPENSE"`.
- **Consolidation Plateforme** : Migration de la logique de génération CSV de `ExportUtil` vers `ReportManager.generateSalesCsv`.
- **Nettoyage UI** : Suppression de la duplication CSV dans `ClosureScreen.kt`. L'écran délègue désormais l'export au `ReportsViewModel`.
- **Isolation Android** : Poursuite de l'élimination du `android.content.Context` dans les ViewModels au profit des services `Platform`.
- **Réduction monolithique** : `MainViewModel` descend à ~480 lignes.
- **Certification technique** : 7 nouveaux tests (CRUD Dépenses, Intégrité CSV, Correction de suite Reports). Total : 103 tests SUCCESS.

---

## 🗓️ 2026-08-19 — Extraction J5.8.4 (ReportsViewModel)
### Actions réalisées :
- **Extraction Domaine Rapports** : Création de `ReportsViewModel` gérant `categorySales`, `dailySales`, `hourlySales`, `expenseBreakdown` et `selectedMonthVentes`.
- **Optimisation de Réactivité** : Remplacement des collecteurs manuels par une chaîne réactive `_currentRange` → `rangeDates` → `flatMapLatest`. Garantit l'annulation des anciennes collectes lors du changement de période (MONTH/YEAR).
- **Consolidation Exports** : Migration d' `exportSalesData` et `shareDailySummary` vers le nouveau ViewModel. Utilisation de `ReportsUiState` pour orchestrer l'UI d'export.
- **Réduction monolithique** : `MainViewModel` descend à ~530 lignes.
- **Certification technique** : 6 nouveaux tests unitaires pour `ReportsViewModel` (Switching de périodes, Historique, Exports CSV). Total tests : 96.

---

## 🗓️ 2026-08-19 — Extraction J5.8.3 (SessionViewModel)
### Actions réalisées :
- **Extraction Domaine Sessions** : Création de `SessionViewModel` gérant `activeSession`, `allSessions`, `allClosures` et les actions Open/Close.
- **Centralisation KPIs** : Migration de `todayProfit` et `todayRevenue` vers `SessionViewModel`. Amélioration de la précision temporelle (ms).
- **Sécurisation Verrouillage** : Ajout d'un flag `isLoaded` pour garantir que `NewSaleScreen` ne ferme pas prématurément lors de l'initialisation asynchrone du flux Room.
- **Réduction monolithique** : `MainViewModel` descend à ~610 lignes.
- **Certification technique** : 4 nouveaux tests unitaires pour `SessionViewModel` (Cycle de vie, Permissions profit). Total tests : 90.

---
*Fin du journal.*
