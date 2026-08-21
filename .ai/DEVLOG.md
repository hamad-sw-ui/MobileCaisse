# # 🪵 JOURNAL DE BORD (DEVLOG)

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
