#!/usr/bin/env bash
###############################################################################
# Ouvre un shell interactif dans l'environnement de développement Docker.
#
#   ./docker/scripts/shell.sh
#
# Le dépôt est monté en lecture-écriture sous /workspace : les modifications
# sont visibles immédiatement des deux côtés.
# Pour un shell JETABLE et sans risque, préférer : ./docker/scripts/sandbox.sh --shell
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

section "Shell de développement MobileCaisse"
cat <<'HINTS'
Commandes utiles dans le conteneur :

  sh ./gradlew assembleDebug        compiler
  sh ./gradlew testDebugUnitTest    tests unitaires
  sh ./gradlew tasks                lister les tâches
  ktlint "app/src/**/*.kt"          formatage
  detekt --input app/src            qualité
  bundletool version                outil AAB

  ⚠️ utiliser « sh ./gradlew » : le wrapper n'est pas exécutable (B-001).

Quitter : exit
HINTS
echo

exec ${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_DEV}" run --rm dev bash
