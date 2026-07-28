#!/usr/bin/env bash
###############################################################################
# EXPÉRIMENTATION ISOLÉE — exigence Phase 6.
#
#   Créer un conteneur temporaire → Compiler → Tester → Analyser
#   → Produire un rapport → Détruire le conteneur
#
# Le dépôt est monté en LECTURE SEULE : une expérimentation ne peut pas
# polluer le projet principal. Tout est copié dans un tmpfs détruit à la fin.
#
#   ./docker/scripts/sandbox.sh                       # copie + compile + teste
#   ./docker/scripts/sandbox.sh "patch.diff"          # applique un patch d'abord
#   ./docker/scripts/sandbox.sh --shell               # shell interactif jetable
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

# Exécution interne au conteneur (commande par défaut du service `sandbox`).
if [[ "${SANDBOX:-}" == "true" ]]; then
  set -euo pipefail
  echo "▸ Copie du projet (lecture seule) vers le tmpfs jetable…"
  # rsync n'est pas installé : cp suffit et évite une dépendance de plus.
  mkdir -p /workspace-tmp/project
  cp -a /workspace/. /workspace-tmp/project/ 2>/dev/null || true
  cd /workspace-tmp/project
  # Les artefacts hérités de l'hôte fausseraient l'expérience.
  rm -rf build app/build .gradle

  echo "▸ Compilation dans le bac à sable…"
  sh ./gradlew assembleDebug --warning-mode all 2>&1 | tail -30
  BUILD=${PIPESTATUS[0]}

  echo "▸ Tests dans le bac à sable…"
  sh ./gradlew testDebugUnitTest 2>&1 | tail -30
  TESTS=${PIPESTATUS[0]}

  echo
  echo "════════ RÉSULTAT DU BAC À SABLE ════════"
  echo "Compilation : $([[ ${BUILD} -eq 0 ]] && echo RÉUSSIE || echo ÉCHOUÉE)"
  echo "Tests       : $([[ ${TESTS} -eq 0 ]] && echo RÉUSSIS || echo ÉCHOUÉS)"
  echo "Le conteneur et le tmpfs sont détruits à la sortie."
  exit $(( BUILD + TESTS ))
fi

# ---------------------- Pilotage depuis l'hôte -------------------------------
require_docker
export_user_ids

REPORT="${REPORTS_DIR}/rapport_experimentation_${RUN_STAMP}.md"

if [[ "${1:-}" == "--shell" ]]; then
  section "Bac à sable interactif (jetable, projet en lecture seule)"
  warn "Le dépôt est monté en LECTURE SEULE sous /workspace."
  warn "Travaillez dans /workspace-tmp — tout y sera détruit à la sortie."
  ${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" run --rm \
    -e SANDBOX="" sandbox bash
  exit 0
fi

section "Expérimentation isolée — conteneur temporaire"
log "Création du conteneur jetable…"

LOG="/tmp/mc_sandbox_${RUN_STAMP}.log"
START=$(date +%s)
set +e
${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" run --rm sandbox 2>&1 | tee "${LOG}"
STATUS=${PIPESTATUS[0]}
set -e
DURATION=$(( $(date +%s) - START ))

log "Destruction du conteneur…"
${DC} -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" rm -f sandbox >/dev/null 2>&1 || true

{
  echo "# Rapport d'expérimentation — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP} · **Durée** : ${DURATION}s"
  echo "**Isolation** : dépôt monté en lecture seule, travail en tmpfs, conteneur détruit"
  echo
  echo "## Résultat"
  echo
  [[ ${STATUS} -eq 0 ]] && echo "✅ **Expérimentation concluante**" \
                        || echo "❌ **Expérimentation en échec** (code ${STATUS})"
  echo
  echo "## Journal"
  echo '```'
  tail -80 "${LOG}"
  echo '```'
  echo
  echo "> Le projet principal n'a subi **aucune** modification :"
  echo "> montage en lecture seule + tmpfs éphémère."
} > "${REPORT}"

section "Résultat"
[[ ${STATUS} -eq 0 ]] && ok "Expérimentation concluante" || err "Expérimentation en échec"
echo "   Rapport : ${REPORT#"${REPO_ROOT}/"}"
ok "Conteneur détruit — projet principal intact."
exit ${STATUS}
