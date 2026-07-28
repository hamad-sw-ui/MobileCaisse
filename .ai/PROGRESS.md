# 📈 JOURNAL DE PROGRESSION

> Une entrée par session, la plus récente en haut.
> Format imposé : Date · Fonctionnalités terminées · Fichiers modifiés ·
> Tests exécutés · Problèmes rencontrés · Étape suivante.

---

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
