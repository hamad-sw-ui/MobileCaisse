#!/usr/bin/env bash
###############################################################################
# Nettoyage de l'environnement Docker et des artefacts de build.
#
#   ./docker/scripts/clean.sh              # conteneurs + artefacts de build
#   ./docker/scripts/clean.sh --caches     # + volumes de cache Gradle/Android
#   ./docker/scripts/clean.sh --all        # + image Docker (reconstruction totale)
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker

LEVEL="${1:-}"

section "Nettoyage — niveau : ${LEVEL:-standard}"

log "Arrêt et suppression des conteneurs…"
${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_DEV}" -f "${COMPOSE_TEST}" down --remove-orphans 2>/dev/null || true
ok "Conteneurs supprimés"

log "Suppression des artefacts de build locaux…"
rm -rf "${REPO_ROOT}/build" "${REPO_ROOT}/app/build" "${REPO_ROOT}/.gradle" "${REPO_ROOT}/.kotlin/errors" 2>/dev/null || true
ok "build/, app/build/, .gradle/, .kotlin/errors/ supprimés"

if [[ "${LEVEL}" == "--caches" || "${LEVEL}" == "--all" ]]; then
  log "Suppression des volumes de cache…"
  warn "Le prochain build retéléchargera Gradle 9.5 et toutes les dépendances (~10 min)."
  docker volume rm -f mobilecaisse-gradle-cache mobilecaisse-android-cache 2>/dev/null || true
  ok "Volumes de cache supprimés"
fi

if [[ "${LEVEL}" == "--all" ]]; then
  log "Suppression de l'image…"
  docker image rm -f mobilecaisse/android-build:1.0.0 2>/dev/null || true
  ok "Image supprimée"
  echo
  warn "Reconstruction nécessaire : ./docker/scripts/build-image.sh"
fi

section "Terminé"
echo "Espace Docker occupé :"
docker system df 2>/dev/null | sed 's/^/  /' || true
echo
echo "Pour purger tout Docker (hors projet) : docker system prune -a"
