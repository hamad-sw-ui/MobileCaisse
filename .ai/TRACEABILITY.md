# # 📑 MATRICE DE TRAÇABILITÉ DES PREUVES

Ce document établit le lien permanent entre les défauts/tâches et les preuves techniques de leur résolution.
**Source de Vérité Finale : Niveau A (Exécution réelle).**

---

## 🏗️ Matrice de Traçabilité

| Bug / Tâche | Analyse d\u0027impact | ADR | Fichiers modifiés | Test(s) associé(s) | Build | Niveau | Documentation | Statut |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **J5.8.2** (Customer ViewModel) | ✅ J5.8.2 | — | `CustomerViewModel.kt`, `MainViewModel.kt`, +4 fichiers | 86 tests SUCCESS | ✅ | A | `STATE.md`, `DEVLOG.md` | **VALIDÉ** |
| **J5.8.3** (Session ViewModel) | ✅ J5.8.3 | — | `SessionViewModel.kt`, `MainViewModel.kt`, +8 fichiers | 90 tests SUCCESS | ✅ | A | `STATE.md`, `DEVLOG.md` | **VALIDÉ** |
| **J5.8.4** (Reports ViewModel) | ✅ J5.8.4 | — | `ReportsViewModel.kt`, `MainViewModel.kt`, +4 fichiers | 96 tests SUCCESS | ✅ | A | `STATE.md`, `DEVLOG.md` | **VALIDÉ** |
| **J5.8.5** (Expense ViewModel) | ✅ J5.8.5 | — | `ExpenseViewModel.kt`, `MainViewModel.kt`, +11 fichiers | 103 tests SUCCESS | ✅ | A | `STATE.md`, `DEVLOG.md` | **VALIDÉ** |
| **J6.1A** (i18n UI FR/EN) | ✅ contre-audit GO | — | 38 fichiers UI + `strings.xml` / `values-en` | 118/118 SUCCESS | ✅ | A | `STATE.md`, `ROADMAP.md`, `DEVLOG.md` | **TERMINÉ** (`82f05be`) |
| **J6.2** (sessions / clôture) | ✅ cartographie + 2 audits | — | 9 fichiers (repos, VM, écrans, 2 tests) | 128/128 SUCCESS | ✅ | A | `STATE.md`, `ROADMAP.md`, `DEVLOG.md` | **TERMINÉ** (`8658ab5`) |
| **J6.3** (permissions / BUG-010) | ✅ audit pré + post-commit | — | 16 fichiers (policy, UI, SMS, BT, caméra, i18n, 1 test) | 141/141 SUCCESS | ✅ `compileDebugKotlin` ; `assembleDebug` **non exécuté** | A-JVM | `STATE.md`, `ROADMAP.md`, `DEVLOG.md` | **TERMINÉ** (`52b4090`) |
| **J6.5** / **B-076** (snackbars) | ✅ contrôle final + post-commit | — | 25 fichiers (LOT 1 = 19, LOT 2 = 2, LOT 3 = 4) | 147/147 SUCCESS | ✅ compile + KSP + **`assembleDebug`** | A | `STATE.md`, `ROADMAP.md`, `DEVLOG.md` | **TERMINÉ** (`498b9ba`) |

---

## 🧪 Détail des Tests (Validations AI-DOS 3.0)

### 1. `ExpenseViewModel` (J5.8.5)
- **Preuve (62ef8fa) :** Extraction du domaine Dépenses. Centralisation de la logique CSV dans `ReportManager`. Suppression d' `ExportUtil`. 7 nouveaux tests (Expense + Reports fix + Platform). Total : 103 tests SUCCESS.

### 2. `ReportsViewModel` (J5.8.4)
- **Preuve (a0eb83a) :** Extraction du domaine Rapports. Correction de la réactivité des flux via `flatMapLatest`. Total : 96 tests SUCCESS.

### 3. J6.2 sessions
- **Preuve (`8658ab5`) :** session unique (mutex + transaction), comptage obligatoire, `closingBalance` = comptage, `expectedBalance` figé avant le manque, 8 ouvertures concurrentes → 1 session. 128/128. I18n J6.1A intacte. Le patch historique n'a pas été appliqué.

### 4. J6.3 permissions
- **Preuve (`52b4090`) :** plus de demande groupée ; `PermissionPolicy.decide` + mapping `SecurityException` ; 13 tests logiques JVM. Suite complète 141/141. `compileDebugKotlin` OK. `assembleDebug` non exécuté. I18n 699/699. J6.2 non modifié.

### 5. J6.5 snackbars / B-076
- **Chaîne :** B-076 → J6.5 → LOT 1 (19) / LOT 2 (2) / LOT 3 (4) → 25 fichiers → 147/147 tests → compile + KSP + `assembleDebug` → commit `498b9ba90509122b7e98d59c96879b0b933889bf` → post-commit audit VALIDÉ.
- **Preuve (`498b9ba`) :** Snackbars locales Material 3 ; Staff / Reports / NewSale / session Error / `uiError` câblés ; vente rapide Stock `Job.join()`. I18n 700/700. J6.2 et J6.3 hors diff. Aucun `.ai/` dans le commit technique.

---

## 🔨 Registre des Builds (Preuves Niveau B)

| Date | Commande | Résultat | Git Hash | Branche | Tâches | Durée |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 2026-09-26 | `:app:compileDebugKotlin` + `:app:kspDebugKotlin` + `:app:testDebugUnitTest` + `:app:assembleDebug --rerun-tasks` | **SUCCESS** (147/147). APK debug généré | `498b9ba` | `arena/...` | J6.5 | 6m 03s |
| 2026-09-26 | `:app:compileDebugKotlin` + `:app:testDebugUnitTest` | **SUCCESS** (141/141). `assembleDebug` **non exécuté** | `52b4090` | `arena/...` | J6.3 | 5m 26s |
| 2026-09-26 | `:app:testDebugUnitTest` + `assembleDebug` | **SUCCESS** (128/128) | `8658ab5` | `arena/...` | J6.2 | 4m 39s |
| 2026-09-26 | `:app:testDebugUnitTest` | **SUCCESS** (118/118) | `82f05be` | `arena/...` | J6.1A | 3m 44s |
| 2026-08-19 | `./gradlew :app:assembleDebug` | **SUCCESS** | `62ef8fa` | `arena/...` | 33 | 2m 36s |
| 2026-08-19 | `./gradlew :app:testDebugUnitTest` | **SUCCESS** | `62ef8fa` | `arena/...` | 33 | 1m 46s |

---

## 📂 Registre Git

| Branche | Commit Initial | Commit Final | État |
| :--- | :--- | :--- | :--- |
| `arena/019fa5ec-mobilecaisse` | `a3a5bfd` | `498b9ba` | **Working tree clean après J6.5 technique ; sync `.ai/` non committée** |
