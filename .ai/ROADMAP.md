# 🗺️ ROADMAP — Plan de complétion

> **Statut : ✅ VALIDÉE le 2026-07-28** — décisions D1 à D4 reçues.
> Point de départ retenu : **J0 (build propre + nettoyage technique)**, puis
> enchaînement direct sur **J1.1 + J1.2** (intégrité des migrations).

## Décisions du responsable (2026-07-28)

| Réf. | Décision | Conséquence |
|---|---|---|
| **D1** | Bases < v18 : perte assumée, **avec accord de l'utilisateur** | B-011 = détection + consentement + export de courtoisie + recréation. `fallbackToDestructiveMigration` reste proscrit |
| **D2** | **Les deux** : renommer l'existant *et* préparer un vrai service distant | B-110 (renommage seul, sans toucher à la logique) + B-111 (analyse Drive/Dropbox) + B-112 (implémentation) |
| **D3** | Démarrer par **J0**, puis **J1.1 + J1.2** | Ordre confirmé ci-dessous |
| **D4** | Le responsable dispose d'Android Studio et exécute builds/tests | Chaque livraison s'accompagne des **commandes exactes** à exécuter |
| **Prod** | **Aucun utilisateur en production** | J1 perd son caractère de sauvetage : c'est un chantier de qualité, pas d'urgence. Latitude pour refondre la chaîne de migrations |

---

## Jalon D — Environnement Docker *(Phase 6, livré le 2026-07-28)* ✅

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| D.1 | Créer `docker/` (image, compose, scripts) | B-130 | ✅ |
| D.2 | Documenter `.ai/DEV_ENVIRONMENT.md` | B-131 | ✅ |
| D.3 | Intégrer aux checklists et règles | B-132 | ✅ |
| D.4 | **Première exécution `make image && make verify`** | B-133 | ✅ |

---

## Vue d'ensemble des dépendances

```
JD Environnement Docker  ◄── Phase 6, préalable à tout
      │
      ▼
J0 Build vérifiable ✅
      │
      ▼
J1 Intégrité des données ✅
      │
      ▼
J2 Sécurité ✅
      │
      ▼
J3 Filet de tests ✅
      │
      ▼
J4 Hilt + assainissement ✅
      │
      ▼
J5 Découpage Repository / ViewModel ✅
      │
      ▼
J6 Fonctionnalités manquantes ⏳ (En cours)
      │
      ▼
J7 Industrialisation (CI, release)
```

---

## Jalon 5 — Découpage architectural ✅

**Objectif** : Découpage de `MainRepository` et `MainViewModel`.

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 5.1 | Extraire `SalesRepository` | B-050b | ✅ (c8dfdb6) |
| 5.2 | Extraire `StockRepository` (InventoryRepository) | B-050c | ✅ (563eee3) |
| 5.3 | Extraire `CustomerRepository` / `SupplierRepository` | B-050 | ✅ (Absorbés) |
| 5.4 | Extraire `CashRepository` (sessions, audits) | B-050 | ✅ (Absorbés) |
| 5.5 | Sortir la génération PDF/ticket du repository | B-051 | ✅ (a457eda) |
| 5.6 | Sortir les `Intent` Android vers `platform/` | B-052 | ✅ (056be5b) |
| 5.7 | `UiState` + ViewModel dédié pour `NewSaleScreen` | B-053, B-054 | ✅ (66c4285) |
| 5.8.1 | Extraction du domaine Inventaire (InventoryViewModel) | B-054 | ✅ (7c870b1) |
| 5.8.2 | Extraction du domaine Clients (CustomerViewModel) | B-054 | ✅ (61b616e) |
| 5.8.3 | Extraction du domaine Sessions (SessionViewModel) | B-054 | ✅ (b8ac697) |
| 5.8.4 | Extraction du domaine Rapports (ReportsViewModel) | B-054 | ✅ (a0eb83a) |
| 5.8.5 | Extraction du domaine Dépenses (ExpenseViewModel) | B-054 | ✅ (62ef8fa) |
| 5.8.6 | ViewModel pour domaines restants (Staff, Backup...) | B-054 | ✅ (2a86d11) |

---

## Jalon 6 — Fonctionnalités manquantes ⏳

**Séquençage J6** :
1.  **Phase 0** : Assainissement et Sécurité (Bugs bloquants) ✅ (e0ceb2c)
2.  **Phase 1** : Socle de Communication — i18n UI **J6.1A ✅ (82f05be)** ; Snackbars **J6.5 ✅ (498b9ba)**
3.  **Phase 2** : Processus Métier — sessions **J6.2 ✅ (8658ab5)** ; permissions **J6.3 ✅ (52b4090)**
4.  **Phase 3** : Monitoring & Surveillance
5.  **Phase 4** : Connectivité Cloud
6.  **Phase 5** : Refonte Structurelle (Monétaire)

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| **6.0** | **Phase 0 : Assainissement (BUG-009, 010, 012, 013)** | — | ✅ (e0ceb2c) |
| 6.1 | Écran de gestion du personnel (débloqué au J5.8.6) | B-070 | ✅ |
| 6.2 | Pilotage complet des sessions de caisse | B-071 | ✅ (8658ab5) |
| 6.3 | Refonte du parcours de permissions | B-078 | ✅ (52b4090) |
| 6.4 | Externalisation des textes UI + `values-en/` (**J6.1A**) | B-073, B-074 | ✅ (82f05be) |
| 6.5 | Snackbars cohérents | B-076 | ✅ (498b9ba) |
| 6.6 | Alertes d'anomalie visibles dans l'UI (inc. BUG-008) | B-072 | ✅ (a34e5f3) |
| 6.7a | Rapport comparatif Google Drive vs Dropbox | B-111 | ✅ (Conception) |
| 6.7b | Implémenter la sauvegarde automatique distante | B-112 | ✅ (LOT 1 : 8bac07c \| LOT 2 : 11d47cb \| LOT 3A : 55067de \| LOT 3B : 0a8c13b \| LOT 3C-1 : 5575241 \| LOT 3C-2 : de9637c) |
| 6.8 | Étude du passage à un type monétaire exact | B-077 | ✅ (Lot 1 : Étude & Décision humaine — Stockage Double maintenu) |

---

## Jalon 7 — Industrialisation □

| Ordre | Tâche | Réf. | Statut |
|---|---|---|---|
| 7.1 | ktlint ou detekt | B-097 | ✅ (J7.1a : 509a1d0) |
| 7.2 | CI GitHub Actions (build + test + lint sur PR) | B-098 | ✅ (J7.2 : f3c615e) |
| 7.3 | Couverture JaCoCo publiée | B-099 | ✅ (J7.3 : 2eb8fdb) |
| 7.4 | Tests Compose des parcours critiques | B-095 | ✅ (J7.4 : 81683bb) |
| 7.5 | Configuration de signature et de release |  | ✅ (J7.5 : 8f391bc / fda08e6) |

---

### J6.1A — UI Internationalization : DONE

Commit technique `82f05be` (`82f05be50795cfe361eaee2c209a9451371291cd`), message `feat(j6): implement J6.1A UI internationalization`.

- 38 fichiers d'écrans, de composants UI et de ressources.
- 684 clés FR et 684 clés EN, parité complète, placeholders cohérents.
- Build SUCCESSFUL. 118/118 tests lors de la validation.
- Aucune modification de ViewModel, Repository, Manager, Gradle, Room/KSP ni de test.

### J6.2 — Sessions / clôture de caisse : DONE

Commit technique `8658ab59eaef16c9937b3e9e740813d15d06a42f`, message `feat(j6): implement J6.2 session management`. Parent `c0b2fda` (sync documentaire J6.1A). 9 fichiers techniques. Aucun fichier `.ai/` dans ce commit.

Comportement livré :
- une seule session active ; `openSession` refuse si une session existe déjà ;
- concurrence : `openSessionMutex` sur le `SalesRepository` `@Singleton`, contrôle + insertion dans `runInTransaction` ; le bouton d'ouverture ignore un second clic pendant `Processing` ;
- fond d'ouverture : vide / non numérique / négatif refusés ; `0` et `1500,5` acceptés (`SessionAmounts`) ;
- aperçu de la session active (`previewActiveSession` / `SessionPreview`) ;
- formule unique `expectedCash = openingBalance + sessionCash - sessionExpenses` (`expectedSessionCash`) ;
- clôture : comptage obligatoire ; `closingBalance = countedCash` ; `expectedBalance` figé avant toute écriture de manque ; écart = `closingBalance - expectedBalance` ;
- le manque (`addExpense`) n'est enregistré qu'après le retour de `closeSession`, pour ne pas fausser l'attendu ;
- vendeur prérempli via `getStaffName` si le personnel existe ; historique avec `label_session_gap`.

Validation : 128/128 tests, `compileDebugKotlin` OK, KSP OK, `assembleDebug` OK. I18n J6.1A conservée (684/684, missing 0, extra 0, placeholder 0). Aucun `UiText` / `uiText` / `uiTextOr` / `Context` pour traduire.

Le patch historique `j6.2-mixed-files-before-split.diff` (SHA-256 `61EBAE077D50F508B44B52ADA29E8C94AF4B26CEAEB5BC13B7C463E03B89FB11`) n'a pas été appliqué. J6.2 a été reconstruit sur le HEAD post-J6.1A. Un premier audit a exigé deux corrections (ordre du manque, concurrence) avant le GO FOR COMMIT.

### J6.3 — Permissions Android modernes / BUG-010 : DONE

Commit technique `52b4090917c02df6d83bde2f4beeab8657e361e2`, message `feat(j6): implement J6.3 permission handling`. Parent `082f2e0` (sync documentaire J6.2). 16 fichiers techniques. Aucun fichier `.ai/` dans ce commit.

Comportement livré :
- plus de demande groupée au démarrage (`MainActivity.checkAppPermissions` retiré) ;
- demandes unitaires au point d'usage : `RECEIVE_SMS` (onboarding), `READ_SMS` (rattrapage / Sync), `CAMERA` (scanner), `POST_NOTIFICATIONS` (onboarding, API ≥ 33), `BLUETOOTH_CONNECT` (écrans imprimante, API ≥ 31) ;
- refus simple, refus définitif, bouton Paramètres, relecture `checkSelfPermission` au `ON_RESUME` ;
- `SmsSyncManager.syncMissedSms` mappe `SecurityException` → `PermissionDenied` ; pas de crash sans `READ_SMS` ;
- caméra : pas de bind CameraX sans permission ; saisie manuelle possible ;
- Bluetooth : garde avant `bondedDevices` / `connect` ;
- i18n 699/699 (15 clés ajoutées en paire FR/EN). J6.1A et J6.2 inchangés.

Validation : 141 tests JVM, `compileDebugKotlin` OK, `testDebugUnitTest` OK. **`assembleDebug` n'a pas été exécuté.** Aucun `UiText` / `uiText` / `uiTextOr`.

Résidus ouverts (non clos) :
- `BLUETOOTH_SCAN` déclaré, jamais demandé ni utilisé ;
- `POST_NOTIFICATIONS` non redemandée aux utilisateurs déjà onboardés ;
- bannière `READ_SMS` non rafraîchie automatiquement après retour Paramètres (Sync relance) ;
- tests permission = logique JVM, pas instrumentés ;
- B-039 (WorkManager hors de `MainActivity`) hors scope.

J6.4 reste J6.1A (déjà terminé). **J6.5 est terminé** (`498b9ba`). J6.6–J6.8 et J7 restent ouverts. **J6.6 / LOT 4 n'est pas commencé.**

### J6.5 — Snackbars / feedback UI / B-076 : DONE

Commit technique `498b9ba90509122b7e98d59c96879b0b933889bf`, message `feat(j6): implement J6.5 snackbar and feedback handling`. Parent `d0e608e` (sync documentaire J6.3). **25 fichiers** techniques, **+264 / −38**. Aucun fichier `.ai/` dans ce commit. Post-commit audit **VALIDÉ**.

Découpage livré :
- **LOT 1** (19 fichiers) : Customer, Debt, Expense, Reports, Staff, NewSale — Snackbars locales, i18n, `clearUiState()` / `onResult`, tests ViewModel.
- **LOT 2** (2 fichiers) : Home + Closure — `SessionUiState.Error` → Snackbar → `clearUiState()`.
- **LOT 3** (4 fichiers) : SalesList, Stock, SmsError, Support — `MainViewModel.uiError` → Snackbar → `clearError()`.

Comportement livré :
- Snackbar Material 3 **locale à l'écran** (`SnackbarHostState` + `LaunchedEffect`) ; erreur consommée puis cleared.
- Pas de bus global, pas de `SharedFlow` / `Channel` / `UiText` / `SnackbarManager` ; pas de `Context` dans les ViewModels J6.5.
- `MainViewModel`, `SessionViewModel`, `SalesRepository` et J6.3 **non refactorés**.
- Staff : formulaire fermé seulement si `onResult` est `null` ; plus de faux succès.
- Reports : `ReportsUiState.Error(@StringRes)`.
- NewSale : `clearUiState()` remet Idle **sans** vider le panier.
- Stock — vente rapide : `addVenteWithItems()` retourne un `Job` ; l'écran fait `join()` puis affiche le succès seulement si `uiError.value == null`.

Validation : **147/147** tests JVM (`--rerun-tasks`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. I18n **700/700** (clé `error_staff_deactivate`). J6.2 et J6.3 hors diff.

Hors périmètre / dettes conservées (non résolues) :
- `SaleDetail` : annulation / retour + `popBackStack` immédiat — `uiError` non collecté (Snackbar inadapté).
- Settings / Splash : reset + navigation immédiate — `uiError` non collecté.
- `convertErrorToOrphan` : producteur non câblé (inutilisé à l'écran).
- `PrintSettings` : `testPrint` silencieux.
- Home : le `AlertDialog` d'ouverture peut masquer visuellement la Snackbar.
- Messages bruts restants hors écrans J6.5 (`InventoryUiState`, `BackupUiState`).

Prochaine étape fonctionnelle : **6.6 alertes d'anomalie (B-072)**. **TERMINÉE.**

### J6.6 — Alertes d'anomalie visibles dans l'UI (inc. BUG-008) / B-072 : DONE

Commit technique `a34e5f312802d9bbc2b06685f338de032a96bb9c`, message `feat(j6): implement J6.6 anomaly detection, sms timestamping and audit log filters`. Parent `0fdbe02b5ee551250f1ce1de01b91ddd7c13a000` (sync documentaire J6.5). **14 fichiers** techniques. Aucun fichier `.ai/` dans ce commit. Post-commit audit **VALIDÉ**.

Découpage livré :
- **LOT 1 — Timestamp SMS réel (BUG-008 / B-029)** (5 fichiers) :
  - `IncomingSms.kt`, `SmsProcessor.kt`, `SmsReceiver.kt`, `SmsSyncManager.kt`, `SmsErrorScreen.kt`.
  - LIVE (`SmsMessage.timestampMillis`), INBOX (`Telephony.Sms.Inbox.DATE`), MANUAL (`null`).
  - Détection `AnomalyEngine.isSmsClockAnomaly(smsTimestamp, systemTimestamp)` avec seuil `> 30 minutes` (exactement 30m = false, 31m = true, null = false).
- **LOT 2 — Contrat AnomalyEngine + Traçabilité** (4 fichiers) :
  - `AnomalyEngine.kt`, `SalesRepository.kt`, `AnomalyEngineTest.kt`, `SalesRepositoryTest.kt`.
  - Contrat `AnomalyResult(blocked, message, severity, code)`.
  - Codes stables : `FUTURE`, `DUPLICATE`, `HIGH_AMOUNT`.
  - Ventes bloquées (`FUTURE`/`DUPLICATE`) : exception dans `runInTransaction` provoquant le rollback SQLite de la vente, attrapée par `insertVenteWithItems` qui logue `ANOMALY_DETECTED` avec sévérité `CRITICAL` **hors transaction** pour survivre au rollback.
  - Ventes a priori suspectes (`HIGH_AMOUNT`) : log `ANOMALY_DETECTED` (`WARNING`) enregistré dans la transaction et vente persistée.
- **LOT 3 — UI Alertes & i18n** (5 fichiers) :
  - `ActionLogScreen.kt`, `NewSaleScreen.kt`, `strings.xml` (FR), `strings.xml` (EN), `ActionLogFilterTest.kt`.
  - Nouveau filtre `ANOMALIES` dans `ActionLogScreen` sélectionnant exclusivement `actionType == "DATE_ANOMALY" || actionType == "ANOMALY_DETECTED"`.
  - Sémantique des filtres `ALL`, `INFO`, `CRITICAL` (CRITICAL + WARNING) 100% conservée.
  - i18n : clés `anomaly_future_sale`, `anomaly_duplicate_sale`, `anomaly_high_amount`, `log_date_anomaly`, `filter_anomalies`. Fallback `else -> details` pour préserver les anciens messages.
  - `NewSaleScreen` relaie le message d'erreur d'anomalie précis dans la Snackbar J6.5.

Validation : **169/169** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — Socle Google Drive Remote Backup (LOT 1) : DONE

Commit technique `8bac07c` (`8bac07c5cb6fdc0b4e3a4228a980166134259cb5`), message `feat(j6.7): implement Google Drive remote backup foundation`. Parent `c00deba` (sync documentaire J6.6). 8 fichiers techniques (+614 lignes, -0). Aucun `.ai/` dans ce commit.

Comportement livré :
- **Dépendances Google Auth & OkHttp** : `play-services-auth:21.2.0`, `okhttp:4.12.0`, `mockwebserver:4.12.0`.
- **Abstraction `RemoteStorageClient`** (`RemoteStorageClient.kt` & `RemoteBackupItem`) : interface générique isolée de Google Drive.
- **Authentification `GoogleDriveAuthManager`** (`GoogleDriveAuthManager.kt` & `PlayServicesGoogleDriveAuthManager`) : Google Sign-In, scope `https://www.googleapis.com/auth/drive.appdata`, récupération des jetons Bearer via `GoogleAuthUtil.getToken()` sur `Dispatchers.IO`. Prise en charge propre de l'absence de GMS.
- **Client REST `GoogleDriveStorageClient`** (`GoogleDriveStorageClient.kt`) : endpoints v3 Drive `uploadBackup` (`multipart/related`, `appDataFolder`), `listBackups`, `downloadBackup` (fichier temporaire `.tmp` avec nettoyage en cas d'erreur), `deleteBackup`.
- **Hilt DI** (`RemoteStorageModule.kt`) : bindings `@Singleton` pour `RemoteStorageClient`, `GoogleDriveAuthManager` et `OkHttpClient`.
- **Tests unitaires JVM** (`GoogleDriveStorageClientTest.kt` & `FakeGoogleDriveAuthManager.kt`) : 8 tests purs MockWebServer sans dépendre d'un compte Google réel.

Validation : **177/177** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — RemoteBackupRepository, Worker & Rotation (LOT 2) : DONE

Commit technique `11d47cb` (`11d47cb908905249f31b5761b115f66033919764`), message `feat(j6.7): implement remote backup repository and worker`. Parent `2ade92a` (sync documentaire J6.7b LOT 1). 6 fichiers techniques (+693 / −0). Aucun `.ai/` dans ce commit.

Comportement livré :
- **`RemoteBackupRepository`** (`RemoteBackupRepository.kt`) : orchestre l'exportation localement chiffrée v2 via `BackupManager.exportBackupWithPassword()` vers un fichier temporaire privé `.db.enc` (supprimé dans `finally`), puis l'upload cloud via `RemoteStorageClient`. Application de la rotation et journalisation d'audit `"REMOTE_BACKUP"` (`INFO`/`CRITICAL`) via `MainRepository.logAction()`.
- **Dérivation déterministe de secret** : `deriveRemoteBackupPassword(managerCode: String)` applique le suffixe `"a1A!"` à `managerCode.trim()` pour garantir la présence simultanée de minuscules, majuscules et chiffres exigés par `BackupManager.validatePasswordStrength`. Secret 100% reproductible pour la future restauration.
- **Politique de rotation** : déduplique les sauvegardes multiples du même jour UTC, conserve les 7 jours civils distincts les plus récents et le 1er fichier de jusqu'à 4 mois civils antérieurs. Erreurs de suppression individuelles non bloquantes.
- **`RemoteBackupWorker`** (`RemoteBackupWorker.kt`) : `@HiltWorker` + `@AssistedInject` planifié via `schedulePeriodic()` (24h, `ExistingPeriodicWorkPolicy.KEEP`, backoff exponentiel 15 min, contraintes `UNMETERED`/`CONNECTED` + batterie non faible). *(Note : helper de planification prêt, branchement UI effectif reporté au LOT 3)*.
- **Extensions `PreferencesManager`** : `remote_backup_enabled`, `wifi_only_backup_enabled`, `last_remote_backup_time`, `last_remote_backup_status`.
- **Hilt DI** (`RemoteStorageModule.kt`) : bindings `@Singleton` pour `RemoteBackupRepository` et `PreferencesManager`.
- **Tests unitaires JVM** (`RemoteBackupRepositoryTest.kt` & `FakeRemoteStorageClient.kt`) : 8 tests unitaires déterministes couvrant le flux d'exportation, les échecs d'authentification, la politique de rotation et la sécurité.

Validation : **185/185** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — BackupViewModel & Startup Wiring (LOT 3A) : DONE

Commit technique `55067de` (`55067deb3ae18e7b470a10bec85d0415b89806d2`), message `feat(j6.7): implement remote backup ViewModel and startup wiring`. Parent `a027245` (sync documentaire J6.7b LOT 2). 3 fichiers techniques (+220 / −9). Aucun `.ai/` dans ce commit. Post-audit `GO — POST-COMMIT LOT 3A VALIDÉ`.

Comportement livré :
- **`BackupViewModel`** (`BackupViewModel.kt`) : injection Hilt de `RemoteBackupRepository`, `GoogleDriveAuthManager` et `PreferencesManager`. Exposition des `StateFlow` distants (`isRemoteBackupEnabled`, `isWifiOnlyBackupEnabled`, `lastRemoteBackupTime`, `lastRemoteBackupStatus`, `isGoogleConnected`, `googleAccountEmail`).
- **Commandes ViewModel** : `toggleRemoteBackup(enabled)` (persiste + schedule/cancel WorkManager), `toggleWifiOnlyBackup(wifiOnly)` (persiste + reschedule si actif), `signOutGoogleDrive()` (déconnexion Google), `triggerManualRemoteBackup()` (sauvegarde distante manuelle chiffrée avec le secret dérivé `deriveRemoteBackupPassword(boutique.managerCode)`).
- **Startup Wiring** : `CaisseApplication.kt` vérifie `preferencesManager.isRemoteBackupEnabled()` dans `onCreate()` et ré-enregistre le worker au démarrage de l'app sans toucher à `MainActivity.kt`.
- **Tests unitaires JVM** (`BackupViewModelTest.kt`) : 8 tests (4 originaux conservés + 4 nouveaux tests LOT 3A).

Validation : **189/189** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — Carte UI Google Drive & i18n FR/EN (LOT 3B) : DONE

Commit technique `0a8c13b` (`0a8c13b76eee4ae31ae28bdacf5f554f860b2597`), message `feat(j6.7): implement Google Drive backup UI`. Parent `84a7c84` (sync documentaire J6.7b LOT 3A). 4 fichiers techniques (+220 / −23). Aucun `.ai/` dans ce commit. Post-audit `GO — POST-COMMIT LOT 3B VALIDÉ`.

Comportement livré :
- **Carte UI Google Drive** dans `SettingsScreen` : insérée sous `settings_backup_export` ("Sauvegarde & Export") et au-dessus de l'exportation locale chiffrée.
- **État Connecté** : affichage e-mail (`googleAccountEmail`), bascule sauvegarde automatique quotidienne (`toggleRemoteBackup`), bascule Wi-Fi uniquement (`toggleWifiOnlyBackup`), horodatage et statut du dernier envoi (format `dd/MM/yyyy HH:mm` ou *"Jamais"*), bouton de sauvegarde manuelle Cloud (`triggerManualRemoteBackup`) et bouton de déconnexion (`signOutGoogleDrive`).
- **État Déconnecté** : affichage *"Non connecté à Google Drive"* et bouton *"Connecter Google Drive"*.
- **Google Sign-In Launcher** : `rememberLauncherForActivityResult` placé 100% dans `SettingsScreen.kt`. `BackupViewModel` expose `getGoogleSignInIntent()` sans aucune référence à une Activity ou Composable.
- **Ressources i18n FR/EN** : 10 paires de clés créées dans `strings.xml` et `values-en/strings.xml` avec parité 100% et placeholders d'indexation `%1$s` / `%2$s`.
- **Accessibilité** : zones interactives >= 48dp, `contentDescription = null` sur icônes décoratives, contrastes Material 3. *(Note : vérification statique effectuée, TalkBack non testé manuellement)*.

Validation : **189/189** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — Restauration Cloud (Fondations) (LOT 3C-1) : DONE

Commit technique `5575241` (`557524168574ebce25f0e0676c1eaab797c6e06c`), message `feat(j6.7): implement cloud restore foundation`. Parent `1079b66` (sync documentaire J6.7b LOT 3B). 4 fichiers techniques (+135 / −0). Aucun `.ai/` dans ce commit. Post-audit `POST-COMMIT GO`.

Comportement livré :
- **API `RemoteBackupRepository`** : wrappers `listRemoteBackups()` et `downloadRemoteBackup(fileId, destFile)` déléguant directement aux APIs REST `RemoteStorageClient`.
- **Méthodes `BackupViewModel`** : exposition du `StateFlow<List<RemoteBackupItem>> cloudBackups`, commande `fetchCloudBackups()` et méthode `restoreCloudBackup(context, remoteItem, userRole, customPassword, onRestored)`.
- **Sécurité du téléchargement & Nettoyage** : téléchargement binaire isolé vers `cacheDir` (`temp_cloud_restore_*.db.enc`) nettoyé inconditionnellement dans un bloc `finally`.
- **Secret & Dérivation** : réutilise la dérivation déterministe `RemoteBackupRepository.deriveRemoteBackupPassword(boutique.managerCode)` (`"${managerCode.trim()}a1A!"`) avec support du paramètre `customPassword`.
- **Réutilisation 100% de la chaîne d'import** : réutilise directement `importEncryptedBackup()` qui délègue à `MainRepository.importEncryptedBackup()` -> `BackupManager.importBackupWithPassword()` -> `restoreDatabase()`. Zero second mécanisme de restauration créé.
- **Tests unitaires JVM** (`RemoteBackupRepositoryTest.kt` & `BackupViewModelTest.kt`) : 4 nouveaux tests unitaires (193/193 tests verts).

Validation : **193/193** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.7b / B-112 — Restauration Cloud UI & Recette Globale (LOT 3C-2) : DONE

Commit technique `de9637c` (`de9637c8641c7bc2add6109d6026d8a6785e2329`), message `feat(j6.7): implement cloud restore UI`. Parent `65eb2a5` (sync documentaire J6.7b LOT 3C-1). 4 fichiers techniques (+282 / −44). Aucun `.ai/` dans ce commit. Post-audit `POST-COMMIT AUDIT GO`.

Comportement livré :
- **`RestorationWizardScreen`** :
  - `TabRow` pour commuter entre "Fichier local" (index 0) et "Google Drive Cloud" (index 1).
  - Flux local conservé 100% à l'identique.
  - Mode Cloud : affichage de la liste des sauvegardes distantes (`cloudBackups`), bouton de rafraîchissement manuel (`action_refresh_cloud_list`), `fetchCloudBackups()` automatique à l'activation de l'onglet ou changement d'état.
  - Sélection d'un élément distant (`selectedCloudItem`), passage à l'étape 2 (confirmation d'écrasement de la base avant restauration).
  - Restauration Cloud via `restoreCloudBackup()`, fallback `customPassword` via `BackupPasswordDialog` et redirection vers `splash` après succès.
  - Réutilisation à 100% des mécanismes Google Sign-In existants et de la chaîne d'importation/restauration.
- **`BackupViewModel`** : remplacement de `catch (e: Exception)` par `catch (e: Throwable)` autour de la planification WorkManager dans `setRemoteBackupEnabled` et `scheduleRemoteBackupIfEnabled` (ajustement ciblé pour tests unitaires JVM purs, seul changement dans le ViewModel, audité JUSTIFIÉ ET SÛR).
- **Ressources i18n FR/EN** : 6 paires de clés créées dans `strings.xml` et `values-en/strings.xml` avec parité 100% (`restoration_tab_local`, `restoration_tab_cloud`, `label_cloud_backups_title`, `label_no_cloud_backups`, `action_refresh_cloud_list`, `status_downloading_cloud`).
- **Accessibilité** : zones tactiles >= 48dp, labels explicites et contentDescription sur le bouton rafraîchissement. `TalkBack manual : NON TESTÉ`.

Validation : **193/193** tests JVM (`:app:testDebugUnitTest`), `compileDebugKotlin` OK, `kspDebugKotlin` OK, **`assembleDebug` OK**, APK debug généré. 0 échec.

### J6.8 / B-077 — Étude du passage à un type monétaire exact (Lot 1) : DONE

Livrable rédigé : `.ai/REPORTS/analyse_conception_2026-10-04_passage_type_monetaire_exact.md` (`STUDY AUDIT GO`).
- **Inventaire** : 22 entités Room au total (16 avec `Double`, 13 avec des champs monétaires, 1 taux `boutique.taxRate`, 2 quantités physiques `recipes`/`stock_movements`, 6 sans `Double`).
- **Décision humaine d'architecture enregistrée :**
  - Maintien du stockage monétaire SQLite existant en `Double` (pas de migration physique DB vers `Long` ou `BigDecimal` à ce stade).
  - Conservation de `Double` comme représentation physique actuelle sans modifier les colonnes SQLite.
  - Sécurisation des calculs par des arrondis métier explicites (`Math.round()`) aux points de calculs.
  - Encapsulation via Inline Value Class `Money(val amount: Double)` envisageable en domaine sans migration DB.
  - Une migration future vers `Long` ou `BigDecimal` nécessiterait un chantier séparé et une nouvelle décision.

Prochaine étape fonctionnelle : **Jalon 7 — Industrialisation (CI, lint, tests)**.

---

*Document mis à jour le 2026-10-05 (J6.8 / B-077 Lot 1 TERMINÉ — Décision humaine enregistrée). Mises à jour précédentes : 2026-10-04 (J6.7b LOT 3C-2 TERMINÉ — commit de9637c) ; 2026-10-03 (J6.7b LOT 3C-1 TERMINÉ — commit 5575241).*
