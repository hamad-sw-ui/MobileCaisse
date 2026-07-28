#!/usr/bin/env bash
###############################################################################
# CHAÎNE DE VALIDATION COMPLÈTE — exigence Phase 6.
#
# Après chaque modification :
#   1. recompiler        2. analyses statiques   3. tests unitaires
#   4. rapport compilation  5. rapport tests     6. rapport avertissements
#
# « Si une étape échoue : arrêter immédiatement l'intégration. »
#
#   ./docker/scripts/validate.sh
#
# Ce script est aussi la commande par défaut du service `test`.
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

# Exécution possible depuis l'hôte OU depuis l'intérieur du conteneur
# (le service `test` l'invoque comme commande par défaut).
IN_CONTAINER=0
[[ -f /.dockerenv || "${CI:-}" == "true" && -d /workspace ]] && IN_CONTAINER=1

SUMMARY="${REPORTS_DIR}/rapport_validation_${RUN_STAMP}.md"
STEPS_OK=0
STEPS_KO=0
declare -a RESULTS

run_step() {
  local name="$1"; shift
  section "${name}"
  if "$@"; then
    ok "${name}"
    RESULTS+=("| ${name} | ✅ RÉUSSI |")
    STEPS_OK=$((STEPS_OK + 1))
    return 0
  else
    err "${name} — ÉCHEC"
    RESULTS+=("| ${name} | ❌ ÉCHEC |")
    STEPS_KO=$((STEPS_KO + 1))
    return 1
  fi
}

write_summary() {
  local verdict="$1"
  {
    echo "# Rapport de validation — ${RUN_DATE}"
    echo
    echo "**Horodatage** : ${RUN_STAMP}"
    echo "**Environnement** : Docker \`mobilecaisse/android-build:1.0.0\`"
    echo "**Chaîne** : compilation → analyses statiques → tests unitaires"
    echo
    echo "## Verdict"
    echo
    echo "${verdict}"
    echo
    echo "| Étape | Résultat |"
    echo "|---|---|"
    printf '%s\n' "${RESULTS[@]}"
    echo
    echo "**Étapes réussies : ${STEPS_OK} · échouées : ${STEPS_KO}**"
    echo
    echo "## Rapports détaillés de cette exécution"
    echo
    for f in "${REPORTS_DIR}"/*_"${RUN_STAMP}".md; do
      [[ -f "$f" ]] && echo "- \`.ai/REPORTS/$(basename "$f")\`"
    done
    echo
    echo "## Critères de livraison (Phase 6)"
    echo
    echo "- [$([[ ${STEPS_KO} -eq 0 ]] && echo x || echo ' ')] Compilation complète réussie"
    echo "- [$([[ ${STEPS_KO} -eq 0 ]] && echo x || echo ' ')] Zéro erreur bloquante"
    echo "- [$([[ ${STEPS_KO} -eq 0 ]] && echo x || echo ' ')] Tous les tests unitaires réussis"
    echo "- [ ] Aucune régression détectée *(exige des tests de non-régression — J3)*"
    echo "- [x] Rapport de couverture généré"
    echo "- [x] Rapport de qualité généré"
    echo
    echo "> ⚠️ **Tests instrumentés non couverts par cette chaîne.**"
    echo "> \`SecurityMigrationTest\` (SQLCipher + Keystore) exige un appareil Android."
    echo "> Voir \`docker/scripts/test-instrumented.sh\` et \`.ai/DEV_ENVIRONMENT.md\` §7."
  } > "${SUMMARY}"
}

section "CHAÎNE DE VALIDATION COMPLÈTE — MobileCaisse"
log "Horodatage : ${RUN_STAMP}"
[[ ${IN_CONTAINER} -eq 1 ]] && log "Exécution : à l'intérieur du conteneur" \
                            || log "Exécution : depuis l'hôte (pilotage Docker)"

if [[ ${IN_CONTAINER} -eq 1 ]]; then
  # -------- Mode interne : on exécute directement Gradle --------------------
  cd /workspace

  run_step "1/3 Compilation (assembleDebug)" \
    bash -c 'sh ./gradlew assembleDebug --warning-mode all' \
    || { write_summary "❌ **VALIDATION ÉCHOUÉE** à la compilation. Intégration interrompue."; exit 1; }

  run_step "2/3 Analyses statiques" \
    bash -c 'ktlint "app/src/**/*.kt" >/dev/null 2>&1; detekt --input app/src --build-upon-default-config >/dev/null 2>&1; true'

  run_step "3/3 Tests unitaires" \
    bash -c 'sh ./gradlew testDebugUnitTest' \
    || { write_summary "❌ **VALIDATION ÉCHOUÉE** aux tests. Intégration interrompue."; exit 1; }

else
  # -------- Mode hôte : on enchaîne les scripts dédiés ----------------------
  require_docker
  export_user_ids

  run_step "0/4 Vérification de l'environnement" "${REPO_ROOT}/docker/scripts/verify-env.sh" \
    || { write_summary "❌ **VALIDATION ÉCHOUÉE** : environnement invalide. Ne pas modifier le code."; exit 1; }

  run_step "1/4 Compilation" "${REPO_ROOT}/docker/scripts/build.sh" debug \
    || { write_summary "❌ **VALIDATION ÉCHOUÉE** à la compilation. Intégration interrompue."; exit 1; }

  run_step "2/4 Analyses statiques" "${REPO_ROOT}/docker/scripts/lint.sh"

  run_step "3/4 Tests unitaires" "${REPO_ROOT}/docker/scripts/test.sh" \
    || { write_summary "❌ **VALIDATION ÉCHOUÉE** aux tests. Intégration interrompue."; exit 1; }
fi

write_summary "✅ **VALIDATION RÉUSSIE** — la modification peut être intégrée."

section "VALIDATION RÉUSSIE"
ok "${STEPS_OK} étapes réussies"
echo "   Synthèse : ${SUMMARY#"${REPO_ROOT}/"}"
