# 📈 JOURNAL DE PROGRESSION

> Une entrée par session, la plus récente en haut.
> Format imposé : Date · Fonctionnalités terminées · Fichiers modifiés ·
> Tests exécutés · Problèmes rencontrés · Étape suivante.

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
