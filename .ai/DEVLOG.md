# 🪵 JOURNAL DE BORD (DEVLOG)

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

## 🗓️ 2026-08-19 — Extraction J5.8.2 (CustomerViewModel)
### Actions réalisées :
- **Extraction Domaine Clients** : Création de `CustomerViewModel` gérant `allCustomers`, `debtors`, `addRepayment` et `updateVipStatus`.
- **Migration SMS** : Ajout de `ShareManager.sendSms(phone, message)` utilisant `ACTION_VIEW`. Purge totale des imports `Intent` et `Uri` dans `DebtScreen` et `CustomerProfileScreen`.
- **Réduction monolithique** : `MainViewModel` descend à ~660 lignes.
- **Réorganisation Repository** : Déplacement de la méthode `updateCustomerVipStatus` de `MainRepository` vers `SalesRepository` pour respect des domaines.
- **Certification technique** : 4 nouveaux tests unitaires pour `CustomerViewModel` (Remboursement, VIP, Filtrage). Build OK.

---

## 🗓️ 2026-08-17 — Extraction J5.8.1 (InventoryViewModel)
### Actions réalisées :
- **Extraction Domaine Inventaire** : Création de `InventoryViewModel` gérant `allStock`, `lowStock`, `audits`, `categories`, `suppliers` et `supplies`.
- **Réduction monolithique** : `MainViewModel` descend à ~690 lignes.
- **Support des domaines** : `MainRepository` conserve la boutique globale et le personnel.
- **Certification technique** : 3 nouveaux tests unitaires pour `InventoryViewModel`. Build OK.

---
*Fin du journal.*
