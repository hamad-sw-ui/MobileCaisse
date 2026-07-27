# 🐞 BUGS

Statuts : `OUVERT` · `EN COURS` · `CORRIGÉ` · `NON REPRODUCTIBLE` · `ACCEPTÉ`
Gravités : 🔴 CRITIQUE · 🟠 MAJEUR · 🟡 MINEUR

> Recensés lors de l'audit du **2026-07-28**. Aucun n'est corrigé à ce jour :
> aucune ligne de code n'a été modifiée depuis la création de ce framework.

---

## 🔴 BUG-001 — Migrations Room désynchronisées des entités

**Statut** : OUVERT · **Gravité** : 🔴 CRITIQUE · **Fichier** : `data/local/AppDatabase.kt`

Six migrations produisent un schéma différent de celui généré depuis les
`@Entity`. Room lève `IllegalStateException: Migration didn't properly handle: <table>`
au premier lancement suivant une mise à jour.

| Migration | Migration crée | Entité attend |
|---|---|---|
| 20→21 `boutique` | `totalQuantityOnReceipt` | `showTotalQuantityOnReceipt` |
| 21→22 `processed_sms` | `status`, `errorMessage` | absentes de l'entité |
| 22→23 `categories` | `description`, `color`, `icon`, index unique `name` | `type` (absent de la migration) |
| 23→24 `suppliers` | `phoneNumber`, `email`, `category`, `notes` | `phone`, `address`, `totalDebt` |
| 24→25 `sessions` | `staffName`, `startCash`, `expectedEndCash`, `actualEndCash`, `status`, FK staff NOT NULL | `sellerName`, `openingBalance`, `closingBalance`, `expectedBalance`, `totalCashSales`, `totalMomoSales`, `totalExpenses`, `isActive`, `staffId` nullable |
| 25→26 `staff` | `permissions`, `createdAt` NOT NULL | `phone`, `pinSalt` |
| 26→27 `processed_sms` | `type` | absente de l'entité |

**Impact** : crash au démarrage pour tout utilisateur existant ; données
inaccessibles. Une installation neuve fonctionne (Room crée depuis les entités),
ce qui masque le bug en test.

**Correction attendue** : activer `exportSchema = true`, générer les schémas de
référence, réécrire les migrations à partir du SQL généré par Room, ajouter des
migrations correctives (`n → 29`) recréant les tables divergentes avec copie des
données, couvrir par `MigrationTestHelper`.

---

## 🔴 BUG-002 — Chaîne de migrations incomplète sous la version 18

**Statut** : OUVERT · **Gravité** : 🔴 CRITIQUE · **Fichier** : `data/local/AppDatabase.kt`

`version = 28`, migrations fournies de 18 à 28 uniquement, aucun
`fallbackToDestructiveMigration`. Une base en version 1–17 ne peut pas être
migrée → crash irrécupérable.

**Correction attendue** : décider (a) migration 1→18 réelle, ou (b) détection +
export de sauvegarde + recréation contrôlée avec consentement explicite.
**Jamais** de destruction silencieuse.

---

## 🟠 BUG-003 — `androidx.appcompat` utilisé mais non déclaré

**Statut** : OUVERT · **Gravité** : 🟠 MAJEUR
**Fichiers** : `MainActivity.kt`, `res/values/themes.xml`, `app/build.gradle.kts`

`MainActivity : AppCompatActivity` et `Theme.CAISSE` hérite de
`Theme.AppCompat.Light.NoActionBar`, mais aucune dépendance `appcompat` n'est
déclarée (ni dans `build.gradle.kts`, ni dans `libs.versions.toml`). Le build ne
tient que par une résolution transitive fortuite.

**Correction attendue** : soit déclarer `androidx.appcompat:appcompat`, soit
(préférable) passer à `ComponentActivity` + `Theme.Material3` et supprimer la
dépendance AppCompat.

---

## 🟠 BUG-004 — Dérivation de clé SQLCipher faible + repli sur `ANDROID_ID`

**Statut** : OUVERT · **Gravité** : 🟠 MAJEUR · **Fichier** : `utils/SecurityUtil.kt`

`deriveNewKey` = **un seul** SHA-256 sur `phone|managerCode|CIPHER_SECRET_V1`
(pas de KDF itératif, pas de sel aléatoire). Le repli `getDatabaseKeyCompat`
dérive la clé de `Settings.Secure.ANDROID_ID`, qui n'est pas un secret et est
lisible par d'autres applications sur les anciennes versions d'Android.

**Correction attendue** : PBKDF2 (≥ 100 000 itérations) ou Argon2 avec sel
aléatoire persisté ; conserver l'ancien chemin uniquement comme voie de
migration à sens unique.

---

## 🟠 BUG-005 — Secret de licence en clair + minification désactivée

**Statut** : OUVERT · **Gravité** : 🟠 MAJEUR
**Fichiers** : `utils/LicenseUtil.kt`, `app/build.gradle.kts`

`SECRET_SALT = "M0M0_C41SS3_V1_PRO_S3CR3T_2024_K3Y"` en clair, et
`generateActivationKey()` (outil admin) est **embarqué dans l'APK de production**.
Avec `isMinifyEnabled = false`, n'importe qui peut décompiler et générer des clés.

**Correction attendue** : activer R8, retirer le générateur de l'APK client,
déplacer le secret (NDK/obfuscation/dérivation), accepter que le modèle
hors-ligne reste imparfait (documenté dans `KNOWN_LIMITATIONS.md`).

---

## 🟠 BUG-006 — SQL construit par concaténation dans `PRAGMA rekey`

**Statut** : OUVERT · **Gravité** : 🟠 MAJEUR · **Fichier** : `data/local/AppDatabase.kt`

`db.execSQL("PRAGMA rekey = '$newKeyPassphrase'")` dans `performRekeyIfNecessary`
et `forceRekey`. La passphrase est hexadécimale (donc sûre en pratique), mais le
motif est dangereux et `forceRekey` ne valide pas son entrée.

**Correction attendue** : valider strictement l'entrée (`^[0-9a-f]{64}$`) et
centraliser dans une fonction unique documentée.

---

## 🟠 BUG-007 — `SavedStateHandle` vide dans `MainViewModelFactory`

**Statut** : OUVERT · **Gravité** : 🟠 MAJEUR · **Fichier** : `ui/viewmodel/MainViewModel.kt:578`

```kotlin
return MainViewModel(application, SavedStateHandle()) as T
```
Un `SavedStateHandle` neuf est créé à chaque fois → `user_role` / `staff_id`
ne sont **jamais** restaurés après mort du processus, contrairement à l'intention.

**Correction attendue** : `AbstractSavedStateViewModelFactory` /
`CreationExtras`, ou `@HiltViewModel` (qui l'injecte correctement).

---

## 🟡 BUG-008 — Détection d'anomalie de date inopérante

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR · **Fichier** : `sms/SmsReceiver.kt:43-46`

```kotlin
val networkTime = System.currentTimeMillis()
val systemTime  = System.currentTimeMillis()
if (abs(networkTime - systemTime) > 30*60*1000) { … }   // toujours faux
```
La protection contre la falsification de l'heure système ne se déclenche jamais.

**Correction attendue** : utiliser le timestamp du centre de service SMS
(`SmsMessage.timestampMillis`) comme référence réseau.

---

## 🟡 BUG-009 — Scope de coroutine orphelin dans `SmsReceiver`

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR · **Fichier** : `sms/SmsReceiver.kt:37`

`CoroutineScope(Dispatchers.IO).launch { }` non annulable, dont dépend
`pendingResult.finish()`. Risque de fuite et de travail tué par le système.

**Correction attendue** : `goAsync()` avec scope maîtrisé et `finally { finish() }`,
ou délégation à un `OneTimeWorkRequest`.

---

## 🟡 BUG-010 — Permissions demandées en bloc, résultat ignoré

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR · **Fichier** : `MainActivity.kt`

Toutes les permissions (SMS, caméra, Bluetooth, notifications) sont demandées au
premier lancement, sans explication, et `onRequestPermissionsResult` n'est pas
implémenté : l'app ignore les refus.

---

## 🟡 BUG-011 — Sauvegarde du fichier `.db` sans checkpoint WAL

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR
**Fichiers** : `data/repository/MainRepository.kt` (`backupDatabase`, `syncToCloud`), `BackupWorker`

Le fichier `caisse_database` est copié alors que le mode `WRITE_AHEAD_LOGGING`
est actif : les fichiers `-wal` et `-shm` ne sont pas copiés → sauvegarde
potentiellement incomplète ou incohérente.

**Correction attendue** : `PRAGMA wal_checkpoint(FULL)` avant copie, ou copier
les trois fichiers, ou utiliser l'API de sauvegarde SQLCipher.

---

## 🟡 BUG-012 — Doublon `NotificationHelper`

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR
**Fichiers** : `notification/NotificationHelper.kt`, `utils/NotificationHelper.kt`

Deux classes homonymes avec des canaux différents (`mobile_caisse_alerts` d'un
côté, `stock_alerts`/`subscription_alerts` de l'autre). `SmsReceiver` utilise la
première, `MainViewModel` et `SubscriptionWorker` la seconde. Confusion garantie.

---

## 🟡 BUG-013 — Liste des routes protégées dupliquée et codée en dur

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR · **Fichier** : `ui/viewmodel/MainViewModel.kt:80`

`isManagerPageRoute()` contient une liste littérale de 10 routes, indépendante de
la `sealed class Screen`. Tout nouvel écran sensible sera **non protégé par oubli**.

**Correction attendue** : porter le niveau d'accès requis dans `Screen`
(ex. `Screen(route, …, requiredRole = Role.MANAGER)`).

---

## 🟡 BUG-014 — Données de démonstration accessibles en production

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR
**Fichiers** : `data/seed/DataSeeder.kt`, `ui/screens/SettingsScreen.kt:133`

`DataSeeder.seedSampleData()` (boutique fictive « Boulangerie Chez Marie »,
ventes et stock factices) est déclenchable depuis l'écran Paramètres du build
release. Risque de pollution de données réelles.

**Correction attendue** : réserver au `BuildConfig.DEBUG`.

---

## 🟡 BUG-015 — Contrainte réseau inutile sur `BackupWorker`

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR · **Fichier** : `MainActivity.kt`

`NetworkType.CONNECTED` est exigé alors que le worker n'effectue **aucun accès
réseau**. Sur un appareil durablement hors ligne, la sauvegarde quotidienne
**ne s'exécute jamais** — exactement le scénario du marché cible.

---

## 🟡 BUG-016 — Déchets versionnés dans le dépôt

**Statut** : OUVERT · **Gravité** : 🟡 MINEUR

- `app/src/main/java/com/reconsiliation/caisse/conversation.txt` — 1 084 lignes
  de transcription d'un autre outil (package `com.rork.momocaisse`), dans le
  source set principal.
- 27 fichiers `.idea/` versionnés (dont `workspace.xml`).
- `.kotlin/errors/*.log` — 7 journaux d'échec du daemon Kotlin.
- `app/logo.png` hors de `res/`.
- `gradlew` non exécutable (mode 644) → `./gradlew` échoue sur Unix/CI.
- `proguard-rules.pro` référencé dans `build.gradle.kts` mais **inexistant**.
- Versions Kotlin incohérentes entre les logs (2.0.21 / 2.2.10) et le catalog (2.1.0).
