# 🏭 Software Factory

Outillage d'exécution du cycle de développement. **Indépendant du code métier**
de MobileCaisse et du Framework IA.

| Couche | Rôle |
|---|---|
| `.ai/` — Framework IA | **définit** les règles |
| `software-factory/` — Software Factory | **exécute** ces règles |
| `docker/` — Environnement | fournit le socle reproductible |

Feuille de route et priorisation : [`ROADMAP.md`](ROADMAP.md).

---

## Une seule commande

```bash
./software-factory/run
```

Pilote un cycle MobileCaisse complet, sans autre intervention :

```
environnement → preflight → autofix → compilation → tests unitaires
  → instrumentation → causes racines → promotion des statuts → publication
```

| Option | Effet |
|---|---|
| `--resume` | reprend un cycle interrompu sans rejouer l'acquis |
| `--status` | état du dernier cycle, sans rien exécuter |
| `--loops N` | boucles corriger→revalider |
| `--push` | pousse le résultat sur la branche courante |
| `--no-emulator` · `--no-autofix` · `--skip-instr` · `--no-promote` | désactivations |

### Promotion automatique des statuts de bug

Après un cycle vert, les bugs passent de `CORRIGÉ (INSPECTION)` à
`CORRIGÉ (VALIDÉ)` — **mais seulement si les preuves obtenues couvrent ce que
le bug exige** (`CODING_RULES.md` §13) :

| Exigence | Preuve nécessaire |
|---|---|
| compilation | étape `build` verte |
| comportement testé | étape `tests` verte |
| SQLCipher / Keystore / migration | étape `instrumentation` verte |
| compatibilité API 24 | instrumentation **sur appareil API 24** |

Exemple réel, cycle avec build + tests verts mais sans instrumentation :

```
✓ BUG-003, 018, 019, 021, 023, 025, 016 → CORRIGÉ (VALIDÉ)
· BUG-020 maintenu — exige une exécution sur appareil API 24
· BUG-011 maintenu — checkpoint WAL, exige une base SQLCipher réelle
· BUG-024 maintenu — concurrence, exige une base réelle
· BUG-022 maintenu — migration staff.pinSalt, exige un test Room
```

**Aucune promotion sur cycle rouge.** C'est l'étape où l'erreur humaine était la
plus fréquente : j'avais moi-même écrit « CORRIGÉ » sur du code jamais compilé,
ce qui a motivé la règle §13.

```
1 détection environnement  → Docker ? JDK ? SDK ? adb ? appareils ?
2 preflight                → analyse statique, sans JDK
3 autofix                  → corrections sûres appliquées
4 compilation              → Docker si présent, sinon JDK local
5 tests unitaires
6 instrumentation          → démarre un émulateur si aucun appareil
7 analyse                  → regroupement par causes racines
8 publication              → last-cycle/ + commit automatique
```

Options : `--no-emulator` · `--no-autofix` · `--skip-instr`

### Reprise après échec

L'état du pipeline est persisté dans `cache/pipeline-state.json`. Après un échec
en compilation, `--resume` **ne rejoue pas** les étapes déjà validées :

```
▸ Reprise du cycle 2026-07-30_231856 (échec à « build »)
  Étapes déjà validées, non rejouées : preflight, autofix
```

Garde-fou : la reprise est refusée si le commit a changé. Des étapes validées
sur un autre code ne prouvent plus rien.

### Architecture du moteur

| Fichier | Rôle |
|---|---|
| `engine/engine.py` | orchestration, boucle, reprise, rapport |
| `engine/runners.py` | une étape = une classe (`applicable`, `retryable`, `mutates_code`) |
| `engine/state.py` | état persistant du pipeline |

Ajouter une étape = ajouter une classe `Runner` et l'inscrire dans `PIPELINE`.
Aucune modification de shell.

Trois propriétés déclarées par chaque runner :
- **`applicable`** — sinon `SKIPPED` avec motif, jamais un échec silencieux ;
- **`retryable`** — une compilation peut échouer sur un verrou Gradle (on relance),
  un test qui échoue échouera encore (on ne relance pas) ;
- **`mutates_code`** — `autofix` invalide les étapes déjà validées après lui.

### Chaque module supprime une intervention humaine précise

| Module | Intervention supprimée |
|---|---|
| `environment/detect.py` | « installer Docker », « où est le JDK ? », « exporter ANDROID_HOME » |
| `environment/emulator.py` | ouvrir Android Studio → Device Manager → démarrer l'AVD → attendre |
| `preflight/` | relire le code à la main avant de compiler |
| `autofix/` | corriger un à un ce que l'analyse détecte |
| `analyzers/` | lire un journal Gradle et deviner la cause racine |
| `publish.py` + `last-cycle/` | copier-coller le journal vers l'agent |
| `engine/state.py` | tout relancer depuis le début après un échec (dont l'image Docker, 5-10 min) |
| `engine/engine.py --max-loops` | enchaîner à la main corriger → relancer → vérifier |
| `engine/promote.py` | relire BUGS.md et promouvoir 13 statuts à la main (~90 min cumulées) |
| `run` | choisir entre 3 points d'entrée documentés |

**Reste manuel** : `git push` — volontairement. Un commit poussé sans relecture
serait une automatisation de trop.

## Architecture

```
software-factory/
├── orchestrator/   pipeline complet
│   ├── full-cycle.sh    ← point d'entrée unique
│   ├── run.py           cycle piloté par Python
│   └── publish.py       canal de retour
├── environment/    détection Docker/JDK/SDK/adb + gestion émulateur
├── preflight/      analyse statique sans JDK
├── autofix/        corrections automatiques sûres
├── analyzers/      journaux Gradle → causes racines
├── last-cycle/     résultat du dernier cycle (VERSIONNÉ)
└── cache/          journaux horodatés (non versionnés)
```

### Stratégie de compilation adaptative

`full-cycle.sh` ne dépend plus de Docker. Il choisit :

1. **Docker** si le démon répond — reproductible, recommandé ;
2. **JDK local** sinon — y compris le JetBrains Runtime d'Android Studio, qui
   embarque un JDK 21 conforme à `toolchainVersion=21` ;
3. **diagnostic actionnable** si aucun des deux, avec les liens d'installation.

Auparavant le pipeline s'arrêtait net sans Docker, alors qu'Android Studio
fournit tout le nécessaire.

---

## SF-02 · `orchestrator` — boucle de validation

```bash
python3 software-factory/orchestrator/run.py             # cycle complet
python3 software-factory/orchestrator/run.py --dry-run   # ce qui est possible ici
python3 software-factory/orchestrator/run.py --analyze journal.log
./software-factory/orchestrator/full-cycle.sh            # tout, sur machine outillée
```

Enchaîne : `preflight → compilation → tests → [instrumentation] → collecte →
causes racines → rapport`.

**Détection d'environnement** : chaque étape se déclare exécutable ou non.
Ce qui ne peut pas tourner est **reporté, jamais simulé** — un rapport
affirmant « compilation réussie » sans compilateur serait un mensonge outillé.

### `analyzers/gradle_log.py`

Classe les erreurs par **cause racine** (§19.2) et distingue les **dérivées**
(§19.3) : une erreur dans `BackupManager.kt` produit trois `unresolved
reference` dans les fichiers qui l'utilisent — l'analyseur les écarte et
remonte la vraie cause en tête.

30 motifs reconnus : Kotlin, KSP/Room, Gradle, tests.

```bash
python3 software-factory/analyzers/test_gradle_log.py   # 10 tests, sans JDK
```

Deux défauts de l'analyseur ont été trouvés par ces tests avant tout usage :
`unresolved` classé prioritaire alors qu'il est presque toujours une
conséquence, et des motifs écrits en casse mixte alors que `_classify` compare
en minuscules (`AEADBadTagException` sortait en « unknown »).

---

## SF-03 · `autofix` — corrections automatiques sûres

```bash
python3 software-factory/autofix/run.py           # simulation (défaut)
python3 software-factory/autofix/run.py --apply   # applique
```

**Rien n'est modifié sans `--apply`.** Une correction silencieuse serait plus
dangereuse que le défaut qu'elle corrige.

Une règle n'est automatisée que si elle est **déterministe**, **vérifiable** et
**sans risque métier**. `SecurityUtil`, `BackupManager`, `LicenseUtil`,
`AppDatabase` et les entités sont en **zone protégée** : jamais modifiés
automatiquement (§13).

`empty-catch` est volontairement en *proposition seule* : les 6 cas corrigés le
2026-07-30 appelaient cinq traitements différents. Un `Log.e` générique
masquerait le vrai besoin.

### Garde-fou appris à l'usage

La première version supprimait `getValue`/`setValue` — opérateurs de délégation
de `by remember`, jamais nommés dans le code. **Cela aurait cassé la
compilation.** La règle exclut désormais les symboles à usage implicite.
Détecté en simulation, avant application.

---

## SF-01 · `preflight` — analyse statique pré-compilation

```bash
python3 software-factory/preflight/run.py            # analyse
python3 software-factory/preflight/run.py --report   # + rapport dans .ai/REPORTS/
python3 software-factory/preflight/run.py --strict   # échoue aussi sur avertissements
```

Sans JDK ni SDK. Code de sortie 0 / 1, utilisable en CI.

### Contrôles

| Contrôle | Détecte | Précédent |
|---|---|---|
| `delimiters` | accolades/parenthèses déséquilibrées | — |
| `min-sdk` | `java.time.`, `java.util.Base64`, `readAllBytes()` | BUG-020 |
| `fileprovider` | autorité divergente du manifeste | **BUG-025** |
| `resources` | `R.xxx.yyy` absente (hors `android.R.*`) | — |
| `duplicate-res` | fichiers de ressources identiques | **CR-1** (861 Ko) |
| `room-migration` | migration ⇄ `@Entity` divergentes | **BUG-001** |
| `empty-catch` · `global-scope` · `db-in-ui` | violations `CODING_RULES` | — |

### Pourquoi ce composant existe

La passe pré-compilation §19.7 avait été refaite **3 fois à la main**, avec des
scripts jetables réécrits à chaque session — qui ont produit **3 faux positifs**
(`android.R.*` pris pour des ressources manquantes, quantificateurs de regex
comptés comme des accolades, `AppCompat` détecté dans un commentaire).

`preflight` a été construit pour supprimer cette répétition **et** ces erreurs.
Sa première exécution a d'ailleurs révélé un quatrième faux positif de ma propre
méthode : une chaîne contenant `"//"` tronquait la ligne analysée. Corrigé dans
`strip_kotlin` — les littéraux sont désormais neutralisés **avant** les
commentaires.

### Ajouter un contrôle

Écrire `check_xxx(ctx) -> list[Finding]` dans `preflight/checks.py`, l'ajouter à
`ALL_CHECKS`, et lui donner une priorité dans `ORDER` (`run.py`).

**Règle** : aucun faux positif toléré. En cas de doute sur un motif, ne rien
signaler plutôt que signaler à tort — un outil qui crie au loup finit désactivé.
