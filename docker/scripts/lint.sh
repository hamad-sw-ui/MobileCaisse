#!/usr/bin/env bash
###############################################################################
# Analyses statiques dans Docker : Android Lint + ktlint + detekt.
# Produit un rapport de qualité unique.
#
#   ./docker/scripts/lint.sh
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

REPORT="${REPORTS_DIR}/rapport_qualite_${RUN_STAMP}.md"
LOG="/tmp/mc_lint_${RUN_STAMP}.log"
OUT_DIR="${REPO_ROOT}/build/quality"
mkdir -p "${OUT_DIR}"

section "Analyses statiques"

# ktlint et detekt sont des CLI autonomes : ils ne dépendent pas du build
# Gradle du projet (aucun plugin qualité n'y est déclaré — B-097).
set +e
${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" run --rm test bash -lc '
  set +e
  mkdir -p build/quality

  echo "───────────────────────── ktlint ─────────────────────────"
  ktlint --reporter=plain --reporter=checkstyle,output=build/quality/ktlint.xml \
         "app/src/**/*.kt" 2>&1 | tail -40
  echo "KTLINT_EXIT=$?"

  echo "───────────────────────── detekt ─────────────────────────"
  detekt --input app/src \
         --report xml:build/quality/detekt.xml \
         --report txt:build/quality/detekt.txt \
         --build-upon-default-config 2>&1 | tail -40
  echo "DETEKT_EXIT=$?"

  echo "─────────────────────── Android Lint ─────────────────────"
  sh ./gradlew lintDebug --continue 2>&1 | tail -40
  echo "ANDROIDLINT_EXIT=$?"
' 2>&1 | tee "${LOG}"
set -e

KTLINT_EXIT=$(grep -oP 'KTLINT_EXIT=\K\d+' "${LOG}" | tail -1 || echo "?")
DETEKT_EXIT=$(grep -oP 'DETEKT_EXIT=\K\d+' "${LOG}" | tail -1 || echo "?")
LINT_EXIT=$(grep -oP 'ANDROIDLINT_EXIT=\K\d+' "${LOG}" | tail -1 || echo "?")

KTLINT_ISSUES=$(grep -c "^app/src.*\.kt:" "${LOG}" || true)
DETEKT_ISSUES=0
[[ -f "${OUT_DIR}/detekt.xml" ]] && DETEKT_ISSUES=$(grep -c "<error" "${OUT_DIR}/detekt.xml" || true)

{
  echo "# Rapport de qualité — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP}"
  echo "**Environnement** : Docker \`mobilecaisse/android-build:1.0.0\`"
  echo
  echo "## Synthèse"
  echo
  echo "| Outil | Code retour | Problèmes |"
  echo "|---|---|---|"
  echo "| ktlint | ${KTLINT_EXIT} | ${KTLINT_ISSUES} |"
  echo "| detekt | ${DETEKT_EXIT} | ${DETEKT_ISSUES} |"
  echo "| Android Lint | ${LINT_EXIT} | voir rapport HTML |"
  echo
  echo "> ⚠️ Première exécution : un volume élevé de signalements est **attendu**."
  echo "> Le projet n'a jamais été passé au formateur (B-097). Ces résultats servent"
  echo "> de **référence initiale** ; la règle est de ne pas les aggraver."
  echo
  echo "## ktlint (formatage Kotlin)"
  echo '```'
  grep "^app/src.*\.kt:" "${LOG}" | head -40 || echo "(aucun problème)"
  echo '```'
  echo
  echo "## detekt (qualité du code)"
  echo '```'
  if [[ -f "${OUT_DIR}/detekt.txt" ]]; then head -40 "${OUT_DIR}/detekt.txt"; else echo "(rapport indisponible)"; fi
  echo '```'
  echo
  echo "## Android Lint"
  echo
  echo "Rapport détaillé : \`app/build/reports/lint-results-debug.html\`"
  echo '```'
  grep -iE "^\s*(Warning|Error):" "${LOG}" | head -30 || echo "(voir rapport HTML)"
  echo '```'
  echo
  echo "## Fichiers de sortie"
  echo
  echo "- \`build/quality/ktlint.xml\` (Checkstyle)"
  echo "- \`build/quality/detekt.xml\` / \`.txt\`"
  echo "- \`app/build/reports/lint-results-debug.html\`"
} > "${REPORT}"

section "Résultat"
ok "Analyses terminées — rapport : ${REPORT#"${REPO_ROOT}/"}"
echo "   ktlint : ${KTLINT_ISSUES} · detekt : ${DETEKT_ISSUES}"
echo
warn "Les analyses statiques ne bloquent PAS la chaîne à ce stade :"
echo "   aucune ligne de base n'existe encore (B-097). Elles le feront une fois"
echo "   les plugins Gradle intégrés et le code formaté une première fois."
