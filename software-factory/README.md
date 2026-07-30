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

## Architecture

```
software-factory/
├── orchestrator/   pilote la boucle complète (SF-02)
│   ├── run.py           cycle + détection d'environnement
│   └── full-cycle.sh    reprise automatique sur machine outillée
├── preflight/      analyse statique sans JDK (SF-01)
├── analyzers/      analyse des journaux Gradle + tests
└── cache/logs/     journaux de build horodatés (non versionnés)
```

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
