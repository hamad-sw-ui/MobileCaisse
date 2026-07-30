# 📮 Canal de retour — dernier cycle exécuté

Ce dossier est **versionné volontairement**, contrairement aux rapports
horodatés de `.ai/REPORTS/`.

Il contient le résultat du dernier `full-cycle.sh` lancé sur une machine
outillée. En le poussant, l'agent le retrouve à la session suivante **sans
aucun copier-coller**.

| Fichier | Contenu |
|---|---|
| `status.json` | résultat machine : étapes, codes retour, causes racines |
| `summary.md` | synthèse lisible |
| `build.log` · `tests.log` | journaux tronqués (2000 dernières lignes) |

Écrasés à chaque cycle : seul le dernier état compte.
