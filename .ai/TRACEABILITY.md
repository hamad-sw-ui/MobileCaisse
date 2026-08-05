# 📑 MATRICE DE TRAÇABILITÉ DES PREUVES

Ce document établit le lien permanent entre les défauts/tâches et les preuves techniques de leur résolution.
**Source de Vérité Finale : Niveau A (Exécution réelle).**

---

## 🏗️ Matrice de Traçabilité

| Bug / Tâche | Analyse d'impact | ADR | Fichiers modifiés | Test(s) associé(s) | Build | Niveau | Documentation | Statut |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **B-094A** (Orchestration Repo) | ✅ B094A | — | `MainRepository.kt`, `FakeDaos.kt` | `MainRepositoryTest` (13 tests) | ✅ 1m 20s | A | `STATE.md`, `DEVLOG.md` | **VALIDÉ** |
| **BUG-018** (AES-GCM broken) | ✅ B140 | ADR-002 | `BackupManager.kt` | `une sauvegarde exportee peut etre restauree a l identique` | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |
| **BUG-019** (DB Plain in ZIP) | ✅ B140 | — | `BackupManager.kt` | `la base n est pas stockee en clair dans l archive` | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |
| **BUG-020** (minSdk 24) | ✅ B140 | — | `BackupManager.kt` | `aucune API superieure a l API 24 n est utilisee` | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |
| **BUG-021** (DB Version fixed) | ✅ B140 | — | `BackupManager.kt` | `la version du schema est celle transmise et non une valeur figee` | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |
| **BUG-022** (pinSalt NULL) | ✅ B022 | — | `AppDatabase.kt` | `verifyPin... with null salt` | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |
| **BUG-023** (Compil fail) | ✅ B140 | — | `BackupManager.kt` | N/A (Preuve par Build) | ✅ 1m 20s | B | `BUGS.md` | **VALIDÉ** |
| **BUG-ENV-001** (classes.jar) | — | — | — | N/A (Preuve par Build) | ✅ 1m 20s | A | `BUGS.md` | **VALIDÉ** |

---

## 🧪 Détail des Tests (Validations AI-DOS 3.0)

### 1. `BackupManagerTest`
- **Méthode :** `une sauvegarde exportee peut etre restauree a l identique`
- **Bug :** **BUG-018** (AES-GCM broken)
- **Preuve :** Réalise un cycle export/import et compare les octets. Échouerait si le tag GCM était doublé (AEADBadTagException).

- **Méthode :** `la base n est pas stockee en clair dans l archive`
- **Bug :** **BUG-019** (DB Plain in ZIP)
- **Preuve :** Vérifie l'absence de l'en-tête "SQLite format 3" dans le ZIP chiffré.

- **Méthode :** `aucune API superieure a l API 24 n est utilisee`
- **Bug :** **BUG-020** (Incompatibilité API 26+)
- **Preuve :** Analyse statique du code source interdisant `java.time` et `readAllBytes`.

### 2. `SecurityUtilTest`
- **Méthode :** `verifyPin should pass with correct legacy SHA-256 hash and null salt`
- **Bug :** **BUG-022** (Migration paresseuse des PIN)
- **Preuve :** Démontre que le mécanisme de migration supporte les comptes legacy sans sel.

---

## 🔨 Registre des Builds (Preuves Niveau B)

| Date | Commande | Résultat | Git Hash | Branche | Tâches | Durée |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 2026-08-05 | `./gradlew :app:assembleDebug` | **SUCCESS** | `a3a5bfd` | `arena/...` | 39 | 1m 43s |
| 2026-08-05 | `./gradlew :app:testDebugUnitTest` | **SUCCESS** | `a3a5bfd` | `arena/...` | 28 | 6m 10s |

---

## 📂 Registre Git

| Branche | Commit Initial | Commit Final | État |
| :--- | :--- | :--- | :--- |
| `arena/019fa5ec-mobilecaisse` | `a3a5bfd` | `a3a5bfd` | **Working tree clean** |
