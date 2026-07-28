# 📋 BACKLOG

Légende : `□` tâche non terminée · `☑` tâche terminée

Le backlog est déduit de l'**écart entre ce que le projet devrait faire et ce
qu'il fait réellement** (audit du 2026-07-28). L'ordre d'exécution est défini
par `ROADMAP.md`.

Identifiants stables : `B-xxx` (ne jamais renuméroter).

> **Rév. 2 — 2026-07-28** : intégration des décisions D1–D4 du responsable et
> révision après vérification des correctifs de sécurité antérieurs
> (voir `BUGS.md` § Note de révision).
> Contexte clé : **aucun utilisateur en production** → les priorités sont
> réordonnées en faveur d'un socle propre plutôt que d'un sauvetage de données.

---

## P0 — Sauver le build et les données (bloquant)

- □ **B-001** Rendre `gradlew` exécutable (`git update-index --chmod=+x gradlew`)
- □ **B-002** Créer `app/proguard-rules.pro` (référencé mais absent)
- □ **B-003** Déclarer `androidx.appcompat` OU migrer vers `ComponentActivity` + `Theme.Material3` *(BUG-003)*
- □ **B-004** Déplacer `sqlcipher` et `sqlite-ktx` du build script vers le version catalog
- □ **B-005** Aligner la version Kotlin réellement utilisée sur `libs.versions.toml` (2.1.0)
- □ **B-006** Vérifier la compatibilité `configuration-cache` + Gradle 9.5 + KSP ; désactiver si instable
- □ **B-007** Supprimer `conversation.txt` du source set principal *(BUG-016)*
- □ **B-008** Nettoyer le dépôt : dé-versionner `.idea/`, `.kotlin/`, déplacer `app/logo.png`, compléter `.gitignore`
- □ **B-009** Activer `exportSchema = true` et versionner `app/schemas/` en Git
- □ **B-010** 🔴 Réécrire les 6 migrations divergentes + migration corrective 28→29 *(BUG-001)*
- □ **B-011** 🔴 Bases < v18 : détection + écran de consentement + export de courtoisie + recréation *(BUG-002 — **décision D1 : perte assumée avec accord de l'utilisateur**)*
- □ **B-012** Écrire les tests `MigrationTestHelper` pour toute la chaîne 18→29
- □ **B-013** Corriger la sauvegarde : checkpoint WAL avant copie du `.db` *(BUG-011)*
- □ **B-101** 🟠 Brancher `BackupManager` (PBKDF2 100k + AES-GCM) sur le parcours réel de sauvegarde/restauration *(BUG-017 — code sécurisé écrit mais inutilisé)*
- □ **B-014** Retirer la contrainte `NetworkType.CONNECTED` de `BackupWorker` *(BUG-015)*

## P1 — Sécurité

- ☑ **B-020** ~~Renforcer la dérivation de clé SQLCipher~~ — **déjà traité** (correctif n°3 : dérivation `phone`+`managerCode`, AndroidKeyStore, rekey auto/manuel, mode standard SQLCipher). Reste optionnel : ajouter un sel aléatoire *(BUG-004 requalifié 🟡, non prioritaire)*
- ☑ **B-021** ~~Migration des bases existantes vers la nouvelle dérivation~~ — **déjà implémentée** (`performRekeyIfNecessary` + `forceRekey`, couvertes par `SecurityMigrationTest`)
- □ **B-022** Activer R8 en release (`isMinifyEnabled`, `isShrinkResources`) + règles keep (Room, SQLCipher, serialization, ML Kit)
- □ **B-023** Retirer `LicenseUtil.generateActivationKey` de l'APK client *(BUG-005)*
- □ **B-024** Sortir `SECRET_SALT` du code source clair *(BUG-005)*
- □ **B-025** Sécuriser et centraliser `PRAGMA rekey` (validation stricte de l'entrée) *(BUG-006)*
- □ **B-026** Réserver `DataSeeder.seedSampleData()` à `BuildConfig.DEBUG` *(BUG-014)*
- □ **B-027** Porter le rôle requis dans `Screen` au lieu de la liste codée en dur *(BUG-013)*
- □ **B-028** Augmenter `PBKDF2_ITERATIONS` pour les PIN (5 000 → ≥ 100 000) — le mécanisme de re-hash paresseux existe déjà ✅, il suffit de relever la constante *(incohérence : `BackupManager` utilise déjà 100 000)*
- □ **B-029** Corriger la détection d'anomalie de date SMS *(BUG-008)*

## P2 — Architecture : Hilt & assainissement

- □ **B-030** Introduire Hilt (plugin, dépendances, `@HiltAndroidApp`, `@AndroidEntryPoint`)
- □ **B-031** `DatabaseModule` : `AppDatabase` + 20 DAO en `@Singleton`
- □ **B-032** `RepositoryModule` + `@ApplicationContext` contraint
- □ **B-033** `@HiltViewModel` sur `MainViewModel` ; supprimer `MainViewModelFactory` *(BUG-007)*
- □ **B-034** `@HiltWorker` + `HiltWorkerFactory` pour `BackupWorker` et `SubscriptionWorker`
- □ **B-035** `EntryPointAccessors` pour `SmsReceiver` (fin des instanciations ad hoc de repository)
- □ **B-036** Fusionner les deux `NotificationHelper` en une seule classe injectée *(BUG-012)*
- □ **B-037** Supprimer les 5 accès directs à `AppDatabase` depuis `ui/`
- □ **B-038** Corriger le scope de coroutine de `SmsReceiver` *(BUG-009)*
- □ **B-039** Déplacer permissions et planification WorkManager hors de `MainActivity`
- □ **B-040** Supprimer le code mort : `PlaceholderScreens.kt`, `StaffDao.getStaffByPin`, `FeeCalculator.calculateMomoFees`, `DataSeeder.seedIfNeeded`
- □ **B-041** Auditer l'usage réel de `BigButton`, `CaisseDialogs`, `CaisseTextFields`, `BackupManager`

## P3 — Architecture : découpage

- □ **B-050** Découper `MainRepository` (992 l.) : `SalesRepository`, `StockRepository`, `CustomerRepository`, `SupplierRepository`, `CashRepository`, `SettingsRepository`
- □ **B-051** Extraire la génération PDF/ticket hors du repository
- □ **B-052** Extraire les `Intent` Android (partage, SMS, USSD) dans une couche `platform/`
- □ **B-053** Introduire un `UiState` par écran (commencer par `NewSaleScreen`)
- □ **B-054** Découper `MainViewModel` (586 l.) en ViewModels par écran
- □ **B-055** Remplacer les statuts en chaînes (`"CONFIRMED"`, `"MANAGER"`…) par des `enum class`
- □ **B-056** Paginer `allVentes` (Paging 3 ou requêtes bornées)
- □ **B-057** Ajouter les index manquants : `vente_items.venteId`, `audit_items.auditId`
- □ **B-058** Transformer `audit_items.auditId` en véritable `ForeignKey`
- □ **B-059** Centraliser la gestion d'erreur (type `AppError` scellé au lieu de `String`)

## P4 — Fonctionnalités manquantes / incomplètes

### Décision D2 — sauvegarde distante (deux volets en parallèle)
- □ **B-110** Volet 1 : renommer honnêtement l'existant — « Exporter et partager » au lieu de « Synchronisation cloud » (libellés UI + `logAction`), **sans toucher à la logique**
- □ **B-111** Volet 2a : rapport d'analyse comparatif Google Drive vs Dropbox (`REPORTS/`)
- □ **B-112** Volet 2b : implémenter la sauvegarde automatique distante retenue (dépend de B-101 : on n'envoie que des archives `BackupManager`)

- □ **B-070** Écran de gestion du personnel (CRUD `staff`) — entité et DAO existent, aucune UI
- □ **B-071** Compléter le pilotage des sessions de caisse (ouverture/fermeture guidée + rapprochement)
- □ **B-072** Enrichir `AnomalyEngine` et exposer les alertes dans l'UI
- □ **B-073** Externaliser tous les textes vers `strings.xml` (aujourd'hui 9 usages de `R.string`)
- □ **B-074** Ajouter la traduction `values-en/`
- ☑ **B-075** ~~Décider du sort de la « sync cloud »~~ — **tranché (D2)** : renommage honnête *et* vrai service distant en parallèle → voir B-110, B-111, B-112
- □ **B-076** Remplacer `Toast`/messages bruts par des `Snackbar` Material 3 cohérents
- □ **B-077** Étudier le passage des montants de `Double` vers un type exact (centimes en `Long`)
- □ **B-078** Refondre le parcours de permissions (contextuel + gestion du refus) *(BUG-010)*

## P5 — Qualité, tests, CI

- □ **B-090** Tests unitaires `SmsParser` (jeu de SMS MTN/Orange réels + pièges)
- □ **B-091** Tests unitaires `FeeCalculator` (barèmes MTN/Orange)
- □ **B-092** Tests unitaires `LicenseUtil` (clé valide, expirée, falsifiée)
- □ **B-093** Tests unitaires `AnomalyEngine`
- □ **B-094** Tests de repository avec base Room en mémoire (vente + stock + recette + dette)
- □ **B-095** Tests instrumentés Compose sur les parcours critiques (nouvelle vente, clôture)
- □ **B-096** Supprimer les tests générés vides (`ExampleUnitTest`, `ExampleInstrumentedTest`)
- □ **B-097** Ajouter ktlint ou detekt + configuration
- □ **B-098** CI GitHub Actions : `assembleDebug`, `test`, `lint` sur chaque PR
- □ **B-099** Mesurer la couverture (JaCoCo) et publier dans `REPORTS/`
- □ **B-100** Rédiger un `README.md` racine (le dépôt n'en a aucun)

---

## Vue d'ensemble

| Priorité | Total | Terminées |
|---|---|---|
| P0 — Build & données | 15 | 0 |
| P1 — Sécurité | 10 | 2 |
| P2 — Hilt & assainissement | 12 | 0 |
| P3 — Découpage | 10 | 0 |
| P4 — Fonctionnalités | 12 | 1 |
| P5 — Qualité & CI | 11 | 0 |
| **TOTAL** | **70** | **3** |

*Dernière mise à jour : 2026-07-28 (rév. 2 — décisions D1–D4 intégrées)*
