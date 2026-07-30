# 🏭 Software Factory — Feuille de route V1

> **Principe** : la Software Factory n'est jamais un objectif en soi. Chaque
> composant est construit **parce qu'une tâche MobileCaisse l'exige**, et il est
> utilisé immédiatement. Aucun composant spéculatif.
>
> Le **Framework IA** (`.ai/`) définit les règles. La **Software Factory**
> (`software-factory/`) les exécute. Les deux restent indépendants du code métier.

---

## Méthode de priorisation

Un composant n'est développé que si :

```
ROI = (nombre de répétitions observées × temps par répétition)
      − coût de construction
```
est **nettement positif**, et que la répétition est **déjà constatée** — pas anticipée.

---

## État des lieux (2026-07-28)

| Constat mesuré | Conséquence |
|---|---|
| Passe pré-compilation §19.7 refaite **3 fois** à la main, avec des scripts jetables réécrits à chaque session | 🔴 candidat n°1 |
| Ces scripts ont produit **3 faux positifs** (regex `{4}`, `android.R.*`, AppCompat en commentaire) | Fiabilité insuffisante |
| **11 bugs** en `CORRIGÉ (INSPECTION)`, aucun validé | Le goulot est la compilation, hors de portée de l'agent |
| `docker/` existe déjà : 13 scripts, chaîne de validation complète | Ne pas réinventer — **réutiliser** |
| Aucun JDK/SDK côté agent | L'analyse **statique** est la seule automatisation exploitable ici |

---

## Composants V1

### SF-01 — `preflight` : analyse statique pré-compilation 🔴 **PRIORITÉ 1**

**Pourquoi maintenant** : seul contrôle exécutable sans compilateur. Refait
3 fois à la main, avec des faux positifs à chaque fois.

Détecte, sans JDK :
- délimiteurs déséquilibrés (en gérant `"""…"""`, `${…}`, commentaires) ;
- API interdites par `minSdk 24` (`java.time.`, `java.util.Base64`, `readAllBytes()`) ;
- autorités `FileProvider` divergentes du manifeste *(aurait attrapé BUG-025)* ;
- ressources `R.xxx.yyy` absentes, en excluant `android.R.*` ;
- doublons de ressources ;
- divergences migration Room ↔ entité *(BUG-001)* ;
- `catch` vides, `GlobalScope`, accès base depuis `ui/`.

**Gain** : ~15 min par session, et surtout **fiabilité** — plus de faux positifs.
**Coût** : 1 session. **ROI : élevé, immédiat.**

### SF-02 — `orchestrator` + `analyzers` ✅ **LIVRÉ 2026-07-30**

**Déclencheur constaté** : 5 sessions consécutives se sont terminées par
« lancez `make validate` et transmettez-moi la sortie », et 2 rapports d'analyse
d'erreurs ont été rédigés à la main. Le chaînage était le goulot.

Livré : `orchestrator/run.py` (cycle + détection d'environnement),
`orchestrator/full-cycle.sh` (reprise automatique), `analyzers/gradle_log.py`
(30 motifs, causes racines, dérivées), `analyzers/test_gradle_log.py` (10 tests).

Validé sur échantillons réalistes — **10/10** — sans JDK.

### SF-03 — `fix` : corrections automatiques sûres 🟡 PRIORITÉ 3

Applique les corrections déterministes uniquement (import manquant, `catch` vide,
autorité `FileProvider`). Refuse tout ce qui touche à la crypto, aux migrations
ou à la logique métier.

**Déclencheur** : après SF-02, sur les motifs d'erreur réellement observés.

### SF-04 — `environment` + pipeline unifié ✅ **LIVRÉ 2026-07-30**

**Interventions supprimées** : le pipeline abandonnait sans Docker ; l'émulateur
devait être lancé à la main depuis Android Studio ; `JAVA_HOME` et
`ANDROID_HOME` devaient être configurés manuellement.

`environment/detect.py` — trouve Docker, JDK (dont le JBR d'Android Studio),
SDK, adb, émulateur, AVD, appareils. Choisit la stratégie de compilation.
`environment/emulator.py --ensure` — démarre un émulateur **seulement** si aucun
appareil n'est connecté, et attend `boot_completed` + fin de l'animation.

`full-cycle.sh` devient le point d'entrée unique en 8 étapes.

### SF-05 — CI GitHub Actions ⚪ PRIORITÉ 5

Réutilise l'image `docker/` sur chaque push. **Déclencheur** : quand le build
passe de façon stable en local.

---

## Ce que la Software Factory ne fera **pas**

| Exclusion | Motif |
|---|---|
| Réimplémenter la chaîne Docker | `docker/` existe et fonctionne |
| Corriger automatiquement crypto, migrations, logique métier | `CODING_RULES.md` §13 — validation humaine obligatoire |
| Remplacer le jugement technique | Elle exécute des règles, elle n'en invente pas |
| Générer du code fonctionnel | Hors périmètre |

---

## Suivi

| # | Composant | Statut | Déclencheur |
|---|---|---|---|
| SF-01 | `preflight` | ✅ **livré 2026-07-28** | 3 répétitions manuelles |
| SF-02 | `orchestrator` + `analyzers` | ✅ **livré 2026-07-30** | 5 demandes manuelles répétées |
| SF-03 | `fix` | ⏳ en attente | **motifs d'erreur réels** issus d'un vrai build |
| SF-04 | `environment` + pipeline | ✅ **livré 2026-07-30** | pipeline bloqué sans Docker |
| SF-05 | Execution Engine | ✅ **livré 2026-07-30** | duplication bash/python, pas de reprise |
| SF-06 | CI GitHub Actions | ⏳ en attente | build stable |
