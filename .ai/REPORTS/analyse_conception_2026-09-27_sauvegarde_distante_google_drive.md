# 🏗️ RAPPORT DE CONCEPTION ET PLAN D'IMPLÉMENTATION — SAUVEGARDE DISTANTE GOOGLE DRIVE (J6.7a / J6.7b)

> **Statut : ✅ VALIDAION HUMAINE DU FOURNISSEUR (2026-09-27)**
> **Choix retenu** : **Google Drive API (espace masqué `appDataFolder`)**
> **Réf. Backlog** : B-111 (J6.7a — Conception/Analyse) et B-112 (J6.7b — Implémentation)

---

## 1. Contexte & Objectifs

L'application MobileCaisse est une application de caisse enregistreuse **offline-first** pour petits commerçants.
Afin de protéger les données d'exploitation irremplaçables contre la perte, le vol ou la casse de l'appareil, un service de sauvegarde distante automatique est requis.

Suite à l'audit factuel comparatif (J6.7a), l'équipe a validé **Google Drive API v3** avec le dossier masqué `appDataFolder` (`drive.appdata`).

### Principes directeurs
1. **Chiffrement Zéro-Connaissance (Zero-Knowledge)** : L'archive `.db.enc` est générée localement par `BackupManager` (chiffrement AES-256-GCM + clé dérivée PBKDF2 100 000 itérations avec le mot de passe du commerçant + manifeste AAD + checksum SHA-256). Google Drive ne reçoit que le blob chiffré.
2. **Protection contre la suppression accidentelle** : Utilisation du dossier d'application masqué `appDataFolder` (`https://www.googleapis.com/auth/drive.appdata`), invisible dans l'interface Google Drive standard de l'utilisateur.
3. **Exécution en arrière-plan réactive** : Planification automatique via `WorkManager` (`RemoteBackupWorker`) avec contraintes de batterie et de réseau (`CONNECTED` ou `UNMETERED`).
4. **Pas de serveur central propriétaire** : Authentification directe entre l'appareil Android et l'API REST Google Drive v3.

---

## 2. Architecture de la solution

```text
[ UI : SettingsScreen / BackupScreen ]
                 │
                 ▼
[ RemoteBackupViewModel ] ──(Statut, Authentification, Sync Manuelle)
                 │
                 ▼
[ GoogleDriveAuthManager ] ──(Google Identity / Credential Manager / OAuth2)
                 │
                 ▼
[ GoogleDriveStorageClient ] ──(Google Drive REST API v3 / appDataFolder)
                 │
                 ▲ (Fichier .db.enc chiffré)
                 │
[ BackupManager ] ──(Chiffrement AES-GCM + PBKDF2 v2 local)
                 ▲
                 │
[ WorkManager : RemoteBackupWorker ] ──(Planification arrière-plan + Rotation)
```

---

## 3. Spécification des composants

### 3.1 `GoogleDriveAuthManager`
* **Rôle** : Gère la connexion OAuth2 Google Drive et le renouvellement des jetons.
* **Scope** : `https://www.googleapis.com/auth/drive.appdata`
* **Méthodes** :
  * `signIn(activityResultLauncher): Unit`
  * `signOut(): Unit`
  * `getAccessToken(): Result<String>` (Récupération ou rafraîchissement du jeton OAuth2)
  * `getConnectedAccountEmail(): String?`

### 3.2 `GoogleDriveStorageClient`
* **Rôle** : Client HTTP léger (OkHttp) effectuant les appels REST vers Google Drive API v3.
* **Endpoints API v3** :
  * `uploadBackup(file: File, metadata: BackupMetadata): Result<String>` : `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart` (dans `spaces='appDataFolder'`).
  * `listBackups(): Result<List<RemoteBackupItem>>` : `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,createdTime,size)`.
  * `downloadBackup(fileId: String, targetFile: File): Result<Unit>` : `GET https://www.googleapis.com/drive/v3/files/{fileId}?alt=media`.
  * `deleteBackup(fileId: String): Result<Unit>` : `DELETE https://www.googleapis.com/drive/v3/files/{fileId}`.

### 3.3 `RemoteBackupWorker` & Politique de rotation
* **Rôle** : Tâche `WorkManager` déclenchée périodiquement (ex: tous les jours).
* **Contraintes** : `NetworkType.CONNECTED` (ou `UNMETERED` si option Wi-Fi activée par l'utilisateur).
* **Politique de rotation** :
  * Conserver au maximum **7 sauvegardes quotidiennes** + **4 sauvegardes mensuelles** dans `appDataFolder`.
  * Après chaque upload réussi, `RemoteBackupWorker` liste les fichiers distants et supprime (`deleteBackup`) les fichiers expirés selon la politique de rétention.

### 3.4 Interface UI & Restauration
* **`SettingsScreen` / `BackupScreen`** :
  * Carte *"Sauvegarde distante Google Drive"*.
  * Affichage de l'état (Connecté à `commercant@gmail.com` / Non connecté).
  * Bouton *"Connecter Google Drive"*, *"Sauvegarder maintenant"*, *"Restaurer depuis le cloud"*.
  * Interrupteur *"Sauvegarde automatique en Wi-Fi uniquement"*.
  * Indication de la date et de l'état du dernier envoi.
* **`RestorationWizardScreen`** :
  * Option de téléchargement depuis Google Drive.
  * Saisie du mot de passe de sauvegarde pour déchiffrer l'archive `.db.enc` via `BackupManager.importBackupWithPassword()`.

---

## 4. Découpage et plan d'implémentation détaillé (J6.7b / B-112)

Le travail sera exécuté en **3 lots contrôlés avec audit indépendant à chaque étape** :

### 📦 LOT 1 — Socle d'authentification et Client REST Google Drive (B-112-LOT1)
1. Déclaration des dépendances Google Identity / OAuth2 nécessaires.
2. Création de `GoogleDriveAuthManager` (gestion de la connexion et des jetons).
3. Création de `GoogleDriveStorageClient` (appels REST v3 pour upload, list, download, delete dans `appDataFolder`).
4. Unit tests JVM isolés pour la construction des requêtes et le parsing JSON des réponses REST Drive.

### 📦 LOT 2 — Repository, WorkManager & Politique de rotation (B-112-LOT2)
1. Création de `RemoteBackupRepository` (liaison entre `BackupManager` et `GoogleDriveStorageClient`).
2. Implémentation du moteur de rotation (purge des sauvegardes distantes > 7 jours / > 4 mois).
3. Création de `RemoteBackupWorker` (`WorkManager`) avec gestion des contraintes réseau, réessai exponentiel et journalisation des erreurs dans `action_logs`.
4. Unit tests JVM pour `RemoteBackupRepository` et la logique de rotation.

### 📦 LOT 3 — UI, Bannières, i18n et Parcours de restauration (B-112-LOT3)
1. Intégration de la carte Google Drive dans l'écran Paramètres/Sauvegarde.
2. Ajout des clés de traduction i18n FR/EN dans `strings.xml` et `values-en/strings.xml`.
3. Intégration du parcours de liste et téléchargement cloud dans l'assistant de restauration.
4. Validation globale (`testDebugUnitTest`, `compileDebugKotlin`, `kspDebugKotlin`, `assembleDebug`).

---

## 5. Critères de recette et d'acceptation (AC-DRIVE-01 à AC-DRIVE-08)

| ID | Critère | Description |
| :--- | :--- | :--- |
| AC-DRIVE-01 | Chiffrement local préalable | Le fichier envoyé sur Google Drive est l'archive binaire v2 chiffrée AES-GCM (`.db.enc`). Aucun SQLite en clair n'est envoyé. |
| AC-DRIVE-02 | Dossier masqué `appDataFolder` | Les fichiers de sauvegarde sont placés dans l'espace `appDataFolder`, invisibles dans le Drive standard de l'utilisateur. |
| AC-DRIVE-03 | Authentification Google Sign-In | L'utilisateur peut connecter son compte Google via le flux natif et se déconnecter à tout moment. |
| AC-DRIVE-04 | Exécution automatique WorkManager | `RemoteBackupWorker` s'exécute en arrière-plan dès que les contraintes réseau/batterie sont satisfaites. |
| AC-DRIVE-05 | Rotation automatique | Les sauvegardes distantes de plus de 7 jours (hors jalons mensuels) sont automatiquement purgées sur Google Drive. |
| AC-DRIVE-06 | Traçabilité des échecs | Tout échec réseau ou d'authentification est consigné dans `action_logs` sans faire crasher l'application. |
| AC-DRIVE-07 | Restauration Cloud | L'utilisateur peut lister les sauvegardes cloud, en télécharger une et la déchiffrer avec son mot de passe de sauvegarde. |
| AC-DRIVE-08 | Respect i18n et accessibilité | Toutes les chaînes UI disposent des paires FR/EN et respectent les règles A11Y. |

---

*Document de conception rédigé le 2026-09-27 pour J6.7a / J6.7b.*
