#!/usr/bin/env bash
###############################################################################
# Compile le projet dans Docker et produit un rapport de compilation.
#
#   ./docker/scripts/build.sh            # assembleDebug
#   ./docker/scripts/build.sh release    # assembleRelease
#   ./docker/scripts/build.sh aab        # bundleRelease (AAB)
#   ./docker/scripts/build.sh clean      # nettoie puis assembleDebug
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

MODE="${1:-debug}"
case "${MODE}" in
  debug)   TASKS="assembleDebug" ;;
  release) TASKS="assembleRelease" ;;
  aab)     TASKS="bundleRelease" ;;
  clean)   TASKS="clean assembleDebug" ;;
  *)       die "Mode inconnu : ${MODE} (attendu : debug | release | aab | clean)" ;;
esac

REPORT="${REPORTS_DIR}/rapport_compilation_${RUN_STAMP}.md"
LOG="/tmp/mc_build_${RUN_STAMP}.log"

section "Compilation (${MODE}) — tâches : ${TASKS}"

START=$(date +%s)
set +e
${DC} -f "${COMPOSE_BASE}" run --rm android \
  bash -lc "sh ./gradlew ${TASKS} --warning-mode all --stacktrace ${GRADLE_EXTRA_ARGS:-}" \
  2>&1 | tee "${LOG}"
STATUS=${PIPESTATUS[0]}
set -e
DURATION=$(( $(date +%s) - START ))

# --- Extraction des indicateurs ----------------------------------------------
WARN_COUNT=$(grep -ciE "^w:|warning:" "${LOG}" || true)
DEPREC_COUNT=$(grep -ci "deprecat" "${LOG}" || true)
ERROR_COUNT=$(grep -ciE "^e:|error:" "${LOG}" || true)

ARTIFACTS=$(find "${REPO_ROOT}/app/build/outputs" \( -name '*.apk' -o -name '*.aab' \) 2>/dev/null | sed "s|${REPO_ROOT}/||" || true)

{
  echo "# Rapport de compilation — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP}"
  echo "**Mode** : ${MODE} (\`${TASKS}\`)"
  echo "**Environnement** : Docker \`mobilecaisse/android-build:1.0.0\` (JDK 21, SDK 35)"
  echo "**Durée** : ${DURATION}s"
  echo
  echo "## Résultat"
  echo
  if [[ ${STATUS} -eq 0 ]]; then
    echo "✅ **COMPILATION RÉUSSIE**"
  else
    echo "❌ **COMPILATION ÉCHOUÉE** (code ${STATUS})"
    echo
    echo "⛔ Conformément à la Phase 6 : arrêter l'intégration, analyser, corriger,"
    echo "puis relancer la chaîne complète."
  fi
  echo
  echo "| Indicateur | Valeur |"
  echo "|---|---|"
  echo "| Erreurs | ${ERROR_COUNT} |"
  echo "| Avertissements | ${WARN_COUNT} |"
  echo "| Dépréciations | ${DEPREC_COUNT} |"
  echo "| Durée | ${DURATION}s |"
  echo
  if [[ -n "${ARTIFACTS}" ]]; then
    echo "## Artefacts produits"
    echo '```'
    echo "${ARTIFACTS}"
    echo '```'
    echo
  fi
  if [[ ${ERROR_COUNT} -gt 0 ]]; then
    echo "## Erreurs"
    echo '```'
    grep -iE "^e:|error:" "${LOG}" | head -40
    echo '```'
    echo
  fi
  echo "## Journal complet (extrait)"
  echo '```'
  tail -60 "${LOG}"
  echo '```'
} > "${REPORT}"

# Rapport d'avertissements séparé (exigence Phase 6).
WARN_REPORT="${REPORTS_DIR}/rapport_avertissements_${RUN_STAMP}.md"
{
  echo "# Rapport des avertissements — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP} · **Mode** : ${MODE}"
  echo
  echo "**Total : ${WARN_COUNT} avertissement(s), dont ${DEPREC_COUNT} dépréciation(s).**"
  echo
  echo "## Avertissements Kotlin / Java"
  echo '```'
  grep -iE "^w:|warning:" "${LOG}" | sort | uniq -c | sort -rn | head -60 || echo "(aucun)"
  echo '```'
  echo
  echo "## Dépréciations"
  echo '```'
  grep -i "deprecat" "${LOG}" | sort -u | head -40 || echo "(aucune)"
  echo '```'
  echo
  echo "> Rappel \`CHECKLISTS/avant_commit.md\` : aucun avertissement critique"
  echo "> nouveau ne doit être introduit par un diff."
} > "${WARN_REPORT}"

section "Résultat"
if [[ ${STATUS} -eq 0 ]]; then
  ok "Compilation réussie en ${DURATION}s (${WARN_COUNT} avertissements)"
else
  err "Compilation échouée (code ${STATUS})"
fi
echo "   Rapport      : ${REPORT#"${REPO_ROOT}/"}"
echo "   Avertissements: ${WARN_REPORT#"${REPO_ROOT}/"}"
exit ${STATUS}
