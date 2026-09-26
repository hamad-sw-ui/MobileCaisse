# # 🪵 JOURNAL DE BORD (DEVLOG)

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
