#!/usr/bin/env bash
###############################################################################
# Exécute les tests UNITAIRES (JVM) dans Docker + rapport de couverture.
#
#   ./docker/scripts/test.sh                    # tous les tests unitaires
#   ./docker/scripts/test.sh "*SmsParser*"      # filtre
#
# ⚠️ Les tests INSTRUMENTÉS (androidTest : SecurityMigrationTest, Espresso)
#    ne sont PAS exécutés ici : ils exigent un appareil Android.
#    Voir ./docker/scripts/test-instrumented.sh et DEV_ENVIRONMENT.md §7.
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

FILTER="${1:-}"
TASK="testDebugUnitTest"
[[ -n "${FILTER}" ]] && TASK="${TASK} --tests \"${FILTER}\""

REPORT="${REPORTS_DIR}/rapport_tests_${RUN_STAMP}.md"
COV_REPORT="${REPORTS_DIR}/rapport_couverture_${RUN_STAMP}.md"
LOG="/tmp/mc_test_${RUN_STAMP}.log"

section "Tests unitaires (JVM, dans Docker)"
[[ -n "${FILTER}" ]] && log "Filtre : ${FILTER}"

START=$(date +%s)
set +e
${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" run --rm test \
  bash -lc "sh ./gradlew ${TASK} --continue ${GRADLE_EXTRA_ARGS:-}" \
  2>&1 | tee "${LOG}"
STATUS=${PIPESTATUS[0]}
set -e
DURATION=$(( $(date +%s) - START ))

# --- Agrégation des résultats XML JUnit --------------------------------------
RESULTS_DIR="${REPO_ROOT}/app/build/test-results/testDebugUnitTest"
TOTAL=0; FAILED=0; SKIPPED=0
if [[ -d "${RESULTS_DIR}" ]]; then
  while IFS= read -r f; do
    t=$(sed -n 's/.*tests="\([0-9]*\)".*/\1/p' "$f" | head -1); TOTAL=$((TOTAL + ${t:-0}))
    x=$(sed -n 's/.*failures="\([0-9]*\)".*/\1/p' "$f" | head -1); FAILED=$((FAILED + ${x:-0}))
    e=$(sed -n 's/.*errors="\([0-9]*\)".*/\1/p'   "$f" | head -1); FAILED=$((FAILED + ${e:-0}))
    s=$(sed -n 's/.*skipped="\([0-9]*\)".*/\1/p'  "$f" | head -1); SKIPPED=$((SKIPPED + ${s:-0}))
  done < <(find "${RESULTS_DIR}" -name 'TEST-*.xml' 2>/dev/null)
fi
PASSED=$(( TOTAL - FAILED - SKIPPED ))

{
  echo "# Rapport des tests — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP}"
  echo "**Environnement** : Docker \`mobilecaisse/android-build:1.0.0\` (conteneur éphémère, sans daemon)"
  echo "**Tâche** : \`${TASK}\`"
  echo "**Durée** : ${DURATION}s"
  echo
  echo "## Synthèse"
  echo
  echo "| Indicateur | Valeur |"
  echo "|---|---|"
  echo "| Total | ${TOTAL} |"
  echo "| Réussis | ${PASSED} |"
  echo "| Échoués | ${FAILED} |"
  echo "| Ignorés | ${SKIPPED} |"
  echo
  if [[ ${STATUS} -eq 0 && ${FAILED} -eq 0 ]]; then
    echo "✅ **TOUS LES TESTS PASSENT**"
  else
    echo "❌ **ÉCHEC** — ${FAILED} test(s) en échec (code Gradle ${STATUS})"
    echo
    echo "⛔ Phase 6 : arrêter l'intégration, corriger, relancer toute la chaîne."
  fi
  echo
  if [[ ${FAILED} -gt 0 ]]; then
    echo "## Tests en échec"
    echo '```'
    grep -A3 -iE "FAILED|AssertionError" "${LOG}" | head -50
    echo '```'
    echo
  fi
  echo "## Périmètre NON couvert ici"
  echo
  echo "Les tests instrumentés exigent un appareil Android et ne peuvent pas"
  echo "s'exécuter dans ce conteneur :"
  echo
  echo "- \`androidTest/.../SecurityMigrationTest.kt\` (2 tests, SQLCipher + Keystore)"
  echo "- \`androidTest/.../ExampleInstrumentedTest.kt\`"
  echo
  echo "→ \`./docker/scripts/test-instrumented.sh\` (exécution sur l'hôte)."
  echo
  echo "## Journal (extrait)"
  echo '```'
  tail -50 "${LOG}"
  echo '```'
} > "${REPORT}"

# --- Couverture ---------------------------------------------------------------
{
  echo "# Rapport de couverture — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP}"
  echo
  EXEC_FILES=$(find "${REPO_ROOT}/app/build" -name '*.exec' -o -name '*.ec' 2>/dev/null | head -5)
  if [[ -n "${EXEC_FILES}" ]]; then
    echo "## Données de couverture détectées"
    echo '```'
    echo "${EXEC_FILES}" | sed "s|${REPO_ROOT}/||"
    echo '```'
  else
    echo "## ⚠️ Couverture non mesurée"
    echo
    echo "Aucun plugin JaCoCo n'est configuré dans \`app/build.gradle.kts\`"
    echo "(vérifié : aucune occurrence de \`jacoco\` dans le build)."
    echo
    echo "\`jacococli\` est disponible dans l'image, mais la **collecte** exige"
    echo "que le build instrumente le bytecode — ce qui nécessite le plugin Gradle."
    echo
    echo "→ Tâche **B-099** du backlog. Tant qu'elle n'est pas faite, la couverture"
    echo "est estimée manuellement dans \`.ai/TEST_PLAN.md\`."
  fi
  echo
  echo "## Estimation actuelle (source : TEST_PLAN.md)"
  echo
  echo "| Périmètre | Cible | Réel |"
  echo "|---|---|---|"
  echo "| \`utils/\` | 90 % | ~15 % |"
  echo "| \`sms/SmsParser\` | 95 % | 0 % |"
  echo "| \`data/repository\` | 70 % | 0 % |"
  echo "| Migrations Room | 100 % des sauts | 0 % |"
  echo
  echo "**Tests réels : ${TOTAL}** (dont 2 générés vides à supprimer — B-096)."
} > "${COV_REPORT}"

section "Résultat"
if [[ ${STATUS} -eq 0 && ${FAILED} -eq 0 ]]; then
  ok "${PASSED}/${TOTAL} tests réussis en ${DURATION}s"
else
  err "${FAILED} test(s) en échec"
fi
echo "   Tests      : ${REPORT#"${REPO_ROOT}/"}"
echo "   Couverture : ${COV_REPORT#"${REPO_ROOT}/"}"
exit ${STATUS}
