# 📈 JOURNAL DE PROGRESSION

> Une entrée par session, la plus récente en haut.
> Format imposé : Date · Fonctionnalités terminées · Fichiers modifiés ·
> Tests exécutés · Problèmes rencontrés · Étape suivante.

---

## 2026-07-28 — Session 4 : réparation de BackupManager (étapes 1–2 du nouvel ordre)

**Date** : 2026-07-28 · **Branche** : `arena/019fa5ec-mobilecaisse`

### Fonctionnalités terminées
- **BUG-023 découvert avant tout le reste** : `BackupManager` **ne compilait pas**.
  `exportBackupWithPassword` déclarait `Result<Unit>` alors que
  `runCatching { … ; Result.success(Unit) }` produit `Result<Result<Unit>>`.
  Preuve définitive que ce fichier n'a jamais été compilé.
- **Réécriture complète du module** (format d'archive **v2**) :
  - BUG-018 — suppression de la gestion manuelle du tag GCM ;
  - BUG-019 — base chiffrée par la clé du mot de passe, **en flux** (8 Ko) ;
  - BUG-020 — `Instant.now()` **et** `readAllBytes()` (API 33, non repéré à
    l'audit) remplacés par des API disponibles depuis l'API 1 ;
  - BUG-021 — `databaseVersion` fourni par l'appelant.
- **Renforcements** : manifeste authentifié via l'**AAD** (une altération du
  nombre d'itérations PBKDF2 invalide l'archive), checksum SHA-256 vérifié après
  déchiffrement, suppression des fichiers partiels en cas d'échec,
  `peekMetadata()`, écart d'identité signalé sans blocage.
- **26 tests unitaires** couvrant les 5 garanties demandées.
- **Harnais de validation indépendant** `tools/verification/verify_backup_format.py` :
  réimplémentation Python du format — **16/16 contrôles réussis**.

### Fichiers modifiés
```
M app/src/main/java/com/reconsiliation/caisse/utils/BackupManager.kt   (réécrit)
A app/src/test/java/com/reconsiliation/caisse/utils/BackupManagerTest.kt  (26 tests)
A tools/verification/verify_backup_format.py + README.md
M app/build.gradle.kts        (commentaire seul — aucune dépendance ajoutée)
M .ai/BUGS.md, BACKLOG.md, CURRENT_TASK.md, PROGRESS.md
```
Aucun écran ni ViewModel modifié : le branchement viendra après validation.

### Tests exécutés
- ✅ **Harnais Python : 16/16** — aller-retour, mauvais mot de passe, base non
  lisible en clair, altérations base/métadonnées/manifeste détectées, nonce
  unique, `databaseVersion = 28`, **BUG-018 reproduit puis corrigé**.
- ✅ Contrôles structurels Kotlin : délimiteurs équilibrés, 26 `@Test`.
- ❌ **Tests Kotlin non exécutés** : ni Docker ni JDK dans l'environnement de
  l'agent. **Exécution par le responsable requise.**

### Problèmes rencontrés
1. **Le module ne compilait pas** (BUG-023) — invalide l'hypothèse selon
   laquelle `BackupManager` était « écrit mais seulement pas branché » : il était
   aussi syntaxiquement invalide.
2. **Second défaut de compatibilité non repéré à l'audit** : `readAllBytes()`
   exige l'API 33. Le test d'analyse du source empêche désormais sa réapparition.
3. **Impossible d'exécuter les tests Kotlin** → contourné par une seconde
   implémentation indépendante en Python, qui valide la logique du format mais
   **ne remplace pas** l'exécution réelle.
4. `javax.xml.bind` utilisé initialement comme oracle Base64 : supprimé du JDK
   depuis la version 11. Remplacé par les vecteurs de la RFC 4648.

### Étape suivante
`./docker/scripts/test.sh "*BackupManager*"` — attendu 26/26.
Puis étape 5 : branchement sur les chemins A, B et C.
Aucun branchement avant tests verts (consigne explicite).

## 2026-07-28 — Session 3 : Phase 6, environnement Docker

**Date** : 2026-07-28 · **Branche** : `arena/019fa5ec-mobilecaisse`

### Fonctionnalités terminées
- **Analyse de valeur préalable** (demandée) : Docker évalué étape par étape.
  Retenu pour compilation, tests unitaires, qualité, packaging.
  **Écarté avec justification** pour l'instrumentation et l'émulateur.
  Argument décisif trouvé dans le dépôt : `.kotlin/errors/*.log` révèle
  **3 versions de Kotlin** (2.0.21, 2.2.10) contre 2.1.0 déclarée — preuve
  matérielle que le projet a été bâti depuis des environnements divergents.
- **Contrainte critique identifiée** : `gradle/gradle-daemon-jvm.properties`
  impose `toolchainVersion=21` → **JDK 21 obligatoire**, alors que le code cible
  `jvmTarget = 11`. Une image JDK 17 aurait échoué.
- **`docker/` créé** : Dockerfile (JDK 21 Temurin, SDK 35, build-tools 35.0.0,
  bundletool, ktlint, detekt, jacococli, git), 3 fichiers compose
  (socle / dev / test+sandbox), 13 scripts, `.dockerignore`, README.
- **`Makefile`** à la racine : 16 cibles (`make help`).
- **`.ai/DEV_ENVIRONMENT.md`** : analyse de valeur, architecture, usage,
  exception instrumentation, rapports, dépannage, interaction avec les défauts connus.
- **Intégration au framework** : `avant_commit.md` (étape 0 « environnement
  validé » + étape 8bis « chaîne complète »), `avant_pull_request.md` (§0
  preuves de validation), `ANDROID_RULES.md` (§9bis), `README.md`,
  `PROMPTS/session_start.md` (étape 0), `BACKLOG.md` (P6), `ROADMAP.md` (jalon D).

### Décisions de conception notables
- **Le code n'est pas copié dans l'image** : contexte de build limité à
  `docker/`, dépôt monté en volume ⇒ modifier le code ne reconstruit jamais l'image.
- **Gradle n'est pas installé** : le wrapper télécharge la version exacte (9.5.0).
  Installer un Gradle système recréerait la divergence à éliminer.
- **Kotlin non installé en binaire système** : fourni par le plugin Gradle 2.1.0.
- **`sh ./gradlew`** partout : contourne B-001 sans modifier de fichier monté.
- **`kotlin.compiler.execution.strategy=in-process`** : neutralise les échecs de
  daemon Kotlin observés dans `.kotlin/errors/`.
- **`configuration-cache=false`** en profil test : contourne B-006.

### Fichiers modifiés
Aucun code de production. Créations :
```
docker/Dockerfile, .dockerignore, README.md
docker/docker-compose{,.dev,.test}.yml
docker/scripts/*.sh                       (13 scripts, tous `bash -n` OK)
Makefile
.ai/DEV_ENVIRONMENT.md
```
Mises à jour : `.ai/README.md`, `ANDROID_RULES.md`, `BACKLOG.md`, `ROADMAP.md`,
`CURRENT_TASK.md`, `CHECKLISTS/avant_commit.md`, `CHECKLISTS/avant_pull_request.md`,
`PROMPTS/session_start.md`.

### Tests exécutés
- ✅ `bash -n` sur les **13 scripts** — aucune erreur de syntaxe.
- ❌ **Image jamais construite, chaîne jamais exécutée** : `docker: command not
  found` dans l'environnement de l'agent. Conformément à D4, la première
  exécution revient au responsable.

### Problèmes rencontrés
1. **Docker indisponible côté agent** → livraison vérifiable syntaxiquement
   seulement. Signalé explicitement dans `DEV_ENVIRONMENT.md` §10.
2. **Couverture non mesurable en l'état** : `jacococli` est dans l'image, mais la
   collecte exige un **plugin Gradle JaCoCo** absent du projet. Le rapport le
   signale au lieu d'inventer un chiffre (B-136).
3. **ktlint/detekt ne bloquent pas encore la chaîne** : sans ligne de base, le
   code n'ayant jamais été formaté, tout bloquer stopperait chaque build (B-135).
4. **BUG-003 fera échouer `make validate`** — anticipé et documenté comme
   résultat attendu.

### Étape suivante
**B-133 (responsable)** : `make image && make verify`, puis transmission de la
sortie. Ensuite, file d'attente de `CURRENT_TASK.md` : réparation de
`BackupManager` → branchement → `pinSalt` → J0 → J1.

Deux arbitrages restent en attente (réparation de `BackupManager` avant
branchement ; portée de la correction BUG-019).

## 2026-07-28 — Session 2 : décisions D1–D4 et révision de l'audit

**Date** : 2026-07-28
**Branche** : `arena/019fa5ec-mobilecaisse`

### Fonctionnalités terminées
- **Vérification du code actuel** contre les 5 correctifs de sécurité annoncés
  par le responsable (audit antérieur Copilot + Gemini). Constat déterminant :
  **le dépôt ne contient qu'un seul commit** — le code audité en session 1
  *était déjà* le code post-correctifs.
  - ✅ Confirmés : suppression de `MASTER_EMERGENCY_2024` (0 occurrence),
    retrait de `fallbackToDestructiveMigration` (0 occurrence), clé DB dérivée
    + Keystore + rekey auto/manuel en mode standard (0 occurrence de `x'...'`),
    PIN PBKDF2 + sel + migration paresseuse.
  - ⚠️ Nuance importante : `BackupManager` (PBKDF2 100k + AES-GCM + checksum)
    est bien écrit, mais **n'est appelé par aucun code applicatif**
    → nouveau **BUG-017**.
- **Révision de `BUGS.md`** (rév. 2) : tableau de confirmation des correctifs,
  BUG-004 requalifié 🟠 → 🟡, BUG-001/002/011 re-vérifiés ligne à ligne et
  maintenus, BUG-005 clarifié (distinct de la porte dérobée), BUG-017 ajouté.
- **Découverte** : la migration 25→26 crée `staff.pinSalt TEXT NOT NULL` alors
  que l'entité le déclare nullable — en conflit direct avec le mécanisme de
  migration paresseuse des PIN (correctif n°5).
- **Intégration des décisions D1–D4** dans `ROADMAP.md` (désormais **validée**),
  `BACKLOG.md`, `DATABASE.md`, `SECURITY.md`, `CURRENT_TASK.md`.
- **Rapport d'analyse D2** rédigé : comparatif Google Drive vs Dropbox vs SAF,
  architecture `RemoteBackupStorage`, plan en 7 étapes.

### Fichiers modifiés
Aucun fichier de code de production. Documentation `.ai/` uniquement :
```
.ai/BUGS.md            rév. 2 — confirmations, requalifications, BUG-017
.ai/SECURITY.md        § 0 « correctifs déjà en place », faiblesses requalifiées
.ai/DATABASE.md        § 0 « acquis à ne pas régresser », D1, conflit pinSalt
.ai/BACKLOG.md         B-020/B-021/B-075 clos ; B-101, B-110/111/112 ajoutés
.ai/ROADMAP.md         statut VALIDÉE, décisions D1–D4, J2 allégé
.ai/CURRENT_TASK.md    J0 + B-110
.ai/PROGRESS.md        cette entrée
.ai/REPORTS/rapport_analyse_2026-07-28_sauvegarde_distante.md   (nouveau)
```

### Tests exécutés
Aucun — environnement toujours sans JDK ni SDK Android. Vérifications
**statiques** par `grep` ciblé sur chacun des 5 correctifs annoncés.
Conformément à **D4**, l'exécution des builds et tests revient au responsable ;
je fournis les commandes exactes.

### Problèmes rencontrés
1. **Écart entre l'audit annoncé et l'état du code** : l'audit antérieur
   mentionne une « vérification complète de la chaîne v18→v27 », or la base est
   en **v28** et les 6 divergences migration ⇄ entité subsistent. BUG-001 est
   maintenu, preuves à l'appui (numéros de ligne).
2. **Sécurité écrite mais non branchée** : `BackupManager` est le meilleur code
   de sécurité du dépôt et il est inutilisé. C'est le type de défaut qu'un audit
   par analyse de fichier isolé ne détecte pas.
3. **Incohérence d'itérations PBKDF2** : 5 000 pour les PIN
   (`SecurityUtil`) contre 100 000 pour les sauvegardes (`BackupManager`).

### Étape suivante
**Jalon 0** (décision D3) : build propre + nettoyage technique + B-110
(renommage « Exporter et partager »). Deux arbitrages attendus avant exécution :
- **B-003** : déclarer AppCompat, ou migrer vers `ComponentActivity` + Material3 ?
- **B-110** : renommer les libellés seuls, ou aussi les identifiants Kotlin ?

Puis **J1.1 + J1.2** : `exportSchema = true` et harnais `MigrationTestHelper`.

---

## 2026-07-28 — Session 1 : Audit et mise en place du framework `.ai/`

**Date** : 2026-07-28
**Branche** : `arena/019fa5ec-mobilecaisse`
**Commit de départ** : `1ca7927` (Initial commit)

### Fonctionnalités terminées
- **Phase 1 — Audit complet** du dépôt en lecture seule :
  - 130 fichiers Kotlin, ~12 000 lignes, mono-module `:app` ;
  - cartographie des couches (UI Compose → `MainViewModel` → `MainRepository` → Room/SQLCipher) ;
  - inventaire des 22 entités, 20 DAO, 10 migrations, 33 routes de navigation ;
  - identification de 16 bugs, dont **2 critiques** (migrations Room) ;
  - identification du code mort et des déchets versionnés.
- **Phase 2 — Création du framework de mémoire persistante `.ai/`** :
  17 fichiers Markdown + 4 sous-dossiers (`PROMPTS/`, `CHECKLISTS/`, `REPORTS/`, `LOGS/`),
  tous renseignés à partir du code réel (aucun contenu générique).
- **Phase 3 — `ROADMAP.md`** : 8 jalons ordonnés par dépendances techniques,
  soumise à validation.

### Fichiers modifiés
Aucun fichier de code de production n'a été modifié.
**Créations uniquement**, toutes sous `.ai/` :

```
.ai/README.md                       .ai/BACKLOG.md
.ai/MISSION.md                      .ai/CURRENT_TASK.md
.ai/PROJECT_CONTEXT.md              .ai/PROGRESS.md
.ai/ARCHITECTURE.md                 .ai/BUGS.md
.ai/CODING_RULES.md                 .ai/KNOWN_LIMITATIONS.md
.ai/ANDROID_RULES.md                .ai/DEPENDENCIES.md
.ai/DATABASE.md                     .ai/SECURITY.md
.ai/API.md                          .ai/TEST_PLAN.md
.ai/ROADMAP.md
.ai/PROMPTS/    (session_start, revue_de_code, roles, nouvelle_fonctionnalite, correction_bug)
.ai/CHECKLISTS/ (avant_commit, avant_pull_request, avant_release, migration_room)
.ai/REPORTS/    (README + 5 modèles + rapport d'audit initial)
.ai/LOGS/       (README + journal 2026-07-28)
```

### Tests exécutés
**Aucun.** L'environnement sandbox ne dispose ni de JDK (`java: command not found`)
ni de SDK Android (`ANDROID_HOME` vide), et `gradlew` n'est pas exécutable.
Vérification **statique uniquement** (lecture de code, `grep`, analyse des schémas).
👉 Le jalon 0 de la roadmap traite précisément ce point.

### Problèmes rencontrés
1. 🔴 **Migrations Room incohérentes** avec les `@Entity` (6 tables) → crash
   garanti à la mise à jour sur base existante. Voir BUG-001.
2. 🔴 **Aucune migration sous la version 18** et aucun repli → base ancienne
   irrécupérable. Voir BUG-002.
3. 🟠 **`androidx.appcompat` utilisé mais non déclaré** → build fragile. BUG-003.
4. 🟠 **Secrets en clair** (licence, dérivation de clé) avec R8 désactivé. BUG-004/005.
5. 🟡 Impossible de compiler pour confirmer les hypothèses (environnement non outillé).
6. 🟡 Dépôt pollué : `conversation.txt` (transcript d'un autre outil, package
   `com.rork.momocaisse`), 27 fichiers `.idea/`, logs `.kotlin/`.

### Étape suivante
En attente de validation du responsable sur `ROADMAP.md` :
- ordre des jalons ;
- point de départ : **J0** (build vérifiable) ou **J1.1–1.2** (schémas Room + harnais de test de migration) ;
- décisions produit **1.4** (bases < v18) et **6.7** (nature de la « sync cloud »).

Aucun code ne sera modifié avant cette validation.
