#!/usr/bin/env bash
###############################################################################
# Tests INSTRUMENTÉS — exécutés HORS Docker, sur l'hôte.
#
# ⚠️ EXCEPTION DOCUMENTÉE à la règle « tout dans Docker » (Phase 6).
#    La Phase 6 le prévoit explicitement : « L'émulateur Android ne devra pas
#    être exécuté dans Docker. Les tests nécessitant un appareil Android
#    devront être exécutés sur un appareil physique connecté ou sur un
#    émulateur lancé sur la machine hôte. »
#
# Justification technique (voir DEV_ENVIRONMENT.md §7) :
#   - l'émulateur exige /dev/kvm et des privilèges étendus ;
#   - SecurityMigrationTest s'appuie sur l'AndroidKeyStore, adossé au matériel
#     et NON émulable de façon fiable en conteneur ;
#   - un faux vert sur un test de sécurité est pire que pas de test du tout.
#
#   ./docker/scripts/test-instrumented.sh
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

REPORT="${REPORTS_DIR}/rapport_tests_instrumentes_${RUN_STAMP}.md"

section "Tests instrumentés (HORS Docker — exception documentée)"

cat <<'EXPLAIN'
Ces tests ne s'exécutent PAS dans Docker, par choix argumenté :

  • SecurityMigrationTest valide SQLCipher + AndroidKeyStore + rekey.
    Le Keystore est adossé au matériel : un émulateur conteneurisé donnerait
    un résultat non représentatif. Sur un test de sécurité, un faux positif
    est plus dangereux que l'absence de test.

  • L'émulateur exige /dev/kvm, --privileged et une configuration hôte
    spécifique, ce qui ruinerait la reproductibilité recherchée.

Docker reste l'environnement officiel pour : compilation, analyses statiques,
tests unitaires, outils de qualité, génération d'APK/AAB.

EXPLAIN

# --- Prérequis hôte ----------------------------------------------------------
if ! command -v adb >/dev/null 2>&1; then
  err "adb introuvable sur l'hôte."
  echo
  echo "  L'adb du conteneur ne suffit pas : il faut un appareil réellement connecté."
  echo "  Installez les Platform Tools, ou lancez ces tests depuis Android Studio."
  exit 1
fi

DEVICES=$(adb devices | grep -cw "device" || true)
if [[ "${DEVICES}" -eq 0 ]]; then
  err "Aucun appareil Android détecté."
  echo
  echo "  Options :"
  echo "   • brancher un téléphone avec le débogage USB activé ;"
  echo "   • démarrer un émulateur depuis Android Studio (Device Manager) ;"
  echo "   • en ligne de commande : emulator -avd <nom_avd>"
  echo
  echo "  Puis vérifier : adb devices"
  exit 1
fi

ok "${DEVICES} appareil(s) détecté(s)"
adb devices | sed 's/^/     /'

# --- Exécution ---------------------------------------------------------------
# L'APK doit être installé sur l'appareil : le build se fait donc sur l'hôte,
# car Gradle pilote adb directement.
log "Exécution de connectedDebugAndroidTest (sur l'hôte)…"
LOG="/tmp/mc_androidtest_${RUN_STAMP}.log"

START=$(date +%s)
set +e
( cd "${REPO_ROOT}" && sh ./gradlew connectedDebugAndroidTest ) 2>&1 | tee "${LOG}"
STATUS=${PIPESTATUS[0]}
set -e
DURATION=$(( $(date +%s) - START ))

{
  echo "# Rapport des tests instrumentés — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP} · **Durée** : ${DURATION}s"
  echo "**Environnement** : ⚠️ **HORS Docker** — hôte + appareil Android"
  echo "**Motif de l'exception** : AndroidKeyStore adossé au matériel, /dev/kvm requis."
  echo
  echo "**Appareils** :"
  echo '```'
  adb devices
  echo '```'
  echo
  echo "## Résultat"
  echo
  [[ ${STATUS} -eq 0 ]] && echo "✅ **TESTS INSTRUMENTÉS RÉUSSIS**" \
                        || echo "❌ **ÉCHEC** (code ${STATUS})"
  echo
  echo "## Tests concernés"
  echo
  echo "- \`SecurityMigrationTest.testFullRekeyMigrationFlow\` — rekey SQLCipher complet"
  echo "- \`SecurityMigrationTest.testMigrationResilienceOnFailure\` — résilience à la corruption"
  echo "- \`ExampleInstrumentedTest\` *(généré, à supprimer — B-096)*"
  echo
  echo "## Journal"
  echo '```'
  tail -60 "${LOG}"
  echo '```'
  echo
  echo "Rapport HTML : \`app/build/reports/androidTests/connected/index.html\`"
} > "${REPORT}"

section "Résultat"
[[ ${STATUS} -eq 0 ]] && ok "Tests instrumentés réussis" || err "Tests instrumentés en échec"
echo "   Rapport : ${REPORT#"${REPO_ROOT}/"}"
exit ${STATUS}
