#!/usr/bin/env bash
# Fonctions partagées par tous les scripts docker/. Jamais exécuté directement.

set -euo pipefail

# Racine du dépôt, quel que soit l'endroit d'où le script est appelé.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export REPO_ROOT

COMPOSE_BASE="${REPO_ROOT}/docker/docker-compose.yml"
COMPOSE_DEV="${REPO_ROOT}/docker/docker-compose.dev.yml"
COMPOSE_TEST="${REPO_ROOT}/docker/docker-compose.test.yml"
export COMPOSE_BASE COMPOSE_DEV COMPOSE_TEST

REPORTS_DIR="${REPO_ROOT}/.ai/REPORTS"
export REPORTS_DIR

# Horodatage commun à tous les rapports d'une même exécution.
export RUN_STAMP="${RUN_STAMP:-$(date +%Y-%m-%d_%H%M%S)}"
export RUN_DATE="${RUN_DATE:-$(date +%Y-%m-%d)}"

# --- Affichage ---------------------------------------------------------------
c_reset=$'\033[0m'; c_red=$'\033[31m'; c_grn=$'\033[32m'
c_yel=$'\033[33m'; c_blu=$'\033[34m'; c_bold=$'\033[1m'

log()  { echo "${c_blu}▸${c_reset} $*"; }
ok()   { echo "${c_grn}✅${c_reset} $*"; }
warn() { echo "${c_yel}⚠️ ${c_reset} $*"; }
err()  { echo "${c_red}❌${c_reset} $*" >&2; }
die()  { err "$*"; exit 1; }

section() {
  echo
  echo "${c_bold}════════════════════════════════════════════════════════════${c_reset}"
  echo "${c_bold} $*${c_reset}"
  echo "${c_bold}════════════════════════════════════════════════════════════${c_reset}"
}

# --- Détection de Docker (hôte uniquement) -----------------------------------
require_docker() {
  command -v docker >/dev/null 2>&1 \
    || die "Docker introuvable. Installez Docker Desktop ou Docker Engine.
       Voir .ai/DEV_ENVIRONMENT.md § Prérequis."

  docker info >/dev/null 2>&1 \
    || die "Le démon Docker ne répond pas. Démarrez Docker puis réessayez."

  if docker compose version >/dev/null 2>&1; then
    DC="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    DC="docker-compose"
  else
    die "docker compose introuvable (plugin v2 ou binaire v1 requis)."
  fi
  export DC
}

# UID/GID de l'hôte, pour que les fichiers générés ne soient pas root:root.
export_user_ids() {
  if [[ "$(uname -s)" == "Linux" ]]; then
    export USER_UID="$(id -u)"
    export USER_GID="$(id -g)"
  else
    # macOS/Windows : Docker Desktop gère déjà la correspondance.
    export USER_UID=1000
    export USER_GID=1000
  fi
}

# Le wrapper est souvent non exécutable dans ce dépôt (B-001) :
# `sh gradlew` fonctionne quel que soit le bit d'exécution.
gradlew() {
  ( cd "${REPO_ROOT}" && sh ./gradlew "$@" )
}
export -f gradlew

mkdir -p "${REPORTS_DIR}"
