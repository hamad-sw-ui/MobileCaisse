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

### SF-02 — `report` : agrégation des sorties de build 🟠 PRIORITÉ 2

Analyse un journal Gradle et produit le rapport §19.6 : total, causes racines,
dérivées, ordre de correction, recompilations économisées.

**Déclencheur** : à construire **quand le premier vrai journal de build
existera**. Le faire avant serait spéculatif — je ne connais pas encore le
format réel des erreurs de ce projet.

### SF-03 — `fix` : corrections automatiques sûres 🟡 PRIORITÉ 3

Applique les corrections déterministes uniquement (import manquant, `catch` vide,
autorité `FileProvider`). Refuse tout ce qui touche à la crypto, aux migrations
ou à la logique métier.

**Déclencheur** : après SF-02, sur les motifs d'erreur réellement observés.

### SF-04 — `cycle` : boucle complète 🟡 PRIORITÉ 4

`preflight → docker build → tests → report → fix → relance`, avec garde-fou :
arrêt après 3 itérations sans progrès.

**Déclencheur** : quand SF-01 à SF-03 sont éprouvés.

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
| SF-02 | `report` | ⏳ en attente | premier journal de build réel |
| SF-03 | `fix` | ⏳ en attente | après SF-02 |
| SF-04 | `cycle` | ⏳ en attente | après SF-03 |
| SF-05 | CI | ⏳ en attente | build stable |
