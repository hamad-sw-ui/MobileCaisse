#!/usr/bin/env bash
###############################################################################
# Génération des livrables APK / AAB dans Docker, et inspection via bundletool.
#
#   ./docker/scripts/package.sh apk-debug
#   ./docker/scripts/package.sh apk-release
#   ./docker/scripts/package.sh aab
#   ./docker/scripts/package.sh aab --apks     # AAB puis .apks universel
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

TARGET="${1:-apk-debug}"
EXTRA="${2:-}"

case "${TARGET}" in
  apk-debug)   TASK="assembleDebug";   OUT="app/build/outputs/apk/debug" ;;
  apk-release) TASK="assembleRelease"; OUT="app/build/outputs/apk/release" ;;
  aab)         TASK="bundleRelease";   OUT="app/build/outputs/bundle/release" ;;
  *) die "Cible inconnue : ${TARGET} (attendu : apk-debug | apk-release | aab)" ;;
esac

section "Génération : ${TARGET}"

if [[ "${TARGET}" != "apk-debug" ]]; then
  warn "Build release : la configuration de signature n'est pas définie dans"
  warn "app/build.gradle.kts. L'artefact sera NON SIGNÉ (unsigned)."
  warn "Voir CHECKLISTS/avant_release.md."
fi

${DC} -f "${COMPOSE_BASE}" run --rm android \
  bash -lc "sh ./gradlew ${TASK} --warning-mode all" || die "Génération échouée."

section "Artefacts produits"
find "${REPO_ROOT}/${OUT}" -type f \( -name '*.apk' -o -name '*.aab' \) 2>/dev/null \
  | while read -r f; do
      printf "  %-60s %s\n" "${f#"${REPO_ROOT}/"}" "$(du -h "$f" | cut -f1)"
    done

# Inspection AAB via bundletool.
if [[ "${TARGET}" == "aab" ]]; then
  AAB=$(find "${REPO_ROOT}/${OUT}" -name '*.aab' | head -1)
  if [[ -n "${AAB}" ]]; then
    section "Inspection de l'AAB (bundletool)"
    ${DC} -f "${COMPOSE_BASE}" run --rm android bash -lc "
      bundletool validate --bundle='${AAB#"${REPO_ROOT}/"}' 2>&1 | head -30
      echo '--- Taille estimée par configuration ---'
      bundletool get-size total --apks=/dev/null 2>/dev/null || true
    " || warn "Inspection bundletool partielle."

    if [[ "${EXTRA}" == "--apks" ]]; then
      section "Génération d'un .apks universel"
      ${DC} -f "${COMPOSE_BASE}" run --rm android bash -lc "
        bundletool build-apks \
          --bundle='${AAB#"${REPO_ROOT}/"}' \
          --output=app/build/outputs/bundle/release/universal.apks \
          --mode=universal
      " && ok "app/build/outputs/bundle/release/universal.apks"
    fi
  fi
fi

ok "Terminé."
