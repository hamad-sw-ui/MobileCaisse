#!/usr/bin/env bash
###############################################################################
# Construit (ou reconstruit) l'image Docker de build MobileCaisse.
#
#   ./docker/scripts/build-image.sh            # build normal (avec cache)
#   ./docker/scripts/build-image.sh --no-cache # reconstruction complète
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

NO_CACHE=""
[[ "${1:-}" == "--no-cache" ]] && NO_CACHE="--no-cache" && log "Reconstruction complète demandée (sans cache)."

section "Construction de l'image mobilecaisse/android-build:1.0.0"
log "UID/GID hôte : ${USER_UID}:${USER_GID}"

# shellcheck disable=SC2086
${DC} -f "${COMPOSE_BASE}" build ${NO_CACHE} android

section "Vérification de l'outillage embarqué"
${DC} -f "${COMPOSE_BASE}" run --rm --no-deps android bash -lc '
  set -e
  printf "%-14s %s\n" "JDK"       "$(java -version 2>&1 | head -1)"
  printf "%-14s %s\n" "Android SDK" "${ANDROID_HOME}"
  printf "%-14s %s\n" "Platforms"  "$(ls ${ANDROID_HOME}/platforms | tr "\n" " ")"
  printf "%-14s %s\n" "BuildTools" "$(ls ${ANDROID_HOME}/build-tools | tr "\n" " ")"
  printf "%-14s %s\n" "adb"        "$(adb version | head -1)"
  printf "%-14s %s\n" "ktlint"     "$(ktlint --version 2>/dev/null || echo indisponible)"
  printf "%-14s %s\n" "detekt"     "$(detekt --version 2>/dev/null || echo indisponible)"
  printf "%-14s %s\n" "bundletool" "$(bundletool version 2>/dev/null || echo indisponible)"
  printf "%-14s %s\n" "jacoco"     "$(jacococli version 2>/dev/null | head -1 || echo indisponible)"
  printf "%-14s %s\n" "git"        "$(git --version)"
'

ok "Image prête."
echo
echo "Étapes suivantes :"
echo "  ./docker/scripts/verify-env.sh   # valider l'environnement"
echo "  ./docker/scripts/validate.sh     # chaîne complète de validation"
