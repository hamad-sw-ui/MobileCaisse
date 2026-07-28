#!/usr/bin/env bash
###############################################################################
# Valide que l'environnement Docker est opérationnel AVANT toute modification
# du code (exigence Phase 6 : « effectuer les modifications uniquement après
# validation de l'environnement »).
#
#   ./docker/scripts/verify-env.sh
###############################################################################
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

require_docker
export_user_ids

REPORT="${REPORTS_DIR}/rapport_environnement_${RUN_STAMP}.md"
FAILURES=0

section "Vérification de l'environnement de développement"

check() {
  local label="$1"; shift
  if "$@" >/tmp/check_out 2>&1; then
    ok "${label}"
    return 0
  else
    err "${label}"
    sed 's/^/     /' /tmp/check_out | head -5
    FAILURES=$((FAILURES + 1))
    return 1
  fi
}

log "Image présente ?"
check "Image mobilecaisse/android-build:1.0.0 disponible" \
  docker image inspect mobilecaisse/android-build:1.0.0

log "Outillage dans le conteneur…"
${DC} -f "${COMPOSE_BASE}" run --rm --no-deps android bash -lc '
  set -e
  fail=0
  chk() { if eval "$2" >/dev/null 2>&1; then echo "  ✅ $1"; else echo "  ❌ $1"; fail=1; fi; }
  chk "JDK 21"            "java -version 2>&1 | grep -q \"21\\.\""
  chk "ANDROID_HOME"      "test -d \$ANDROID_HOME"
  chk "platform android-35" "test -d \$ANDROID_HOME/platforms/android-35"
  chk "build-tools 35"    "test -d \$ANDROID_HOME/build-tools/35.0.0"
  chk "adb"               "command -v adb"
  chk "ktlint"            "command -v ktlint"
  chk "detekt"            "command -v detekt"
  chk "bundletool"        "command -v bundletool"
  chk "jacococli"         "command -v jacococli"
  chk "git"               "command -v git"
  chk "projet monté"      "test -f /workspace/settings.gradle.kts"
  exit $fail
' || FAILURES=$((FAILURES + 1))

log "Gradle démarre-t-il ? (télécharge Gradle 9.5 au premier lancement — soyez patient)"
if ${DC} -f "${COMPOSE_BASE}" run --rm android bash -lc 'sh ./gradlew --version' > /tmp/gradle_ver 2>&1; then
  ok "Gradle opérationnel"
  grep -E "Gradle|JVM|Kotlin" /tmp/gradle_ver | sed 's/^/     /'
else
  err "Gradle n'a pas démarré"
  tail -20 /tmp/gradle_ver | sed 's/^/     /'
  FAILURES=$((FAILURES + 1))
fi

# --- Rapport -----------------------------------------------------------------
{
  echo "# Rapport d'environnement — ${RUN_DATE}"
  echo
  echo "**Horodatage** : ${RUN_STAMP}"
  echo "**Image** : \`mobilecaisse/android-build:1.0.0\`"
  echo "**Hôte** : $(uname -s) $(uname -m)"
  echo "**Docker** : $(docker --version)"
  echo
  echo "## Résultat"
  echo
  if [[ ${FAILURES} -eq 0 ]]; then
    echo "✅ **Environnement validé** — ${FAILURES} échec."
    echo
    echo "Le développement peut commencer."
  else
    echo "❌ **Environnement invalide** — ${FAILURES} échec(s)."
    echo
    echo "⛔ Ne pas modifier le code tant que ces échecs ne sont pas résolus."
  fi
  echo
  echo "## Outillage détecté"
  echo '```'
  cat /tmp/gradle_ver 2>/dev/null | grep -E "Gradle|JVM|Kotlin|Launcher" || echo "(Gradle non démarré)"
  echo '```'
} > "${REPORT}"

section "Résultat"
if [[ ${FAILURES} -eq 0 ]]; then
  ok "Environnement validé — rapport : ${REPORT#"${REPO_ROOT}/"}"
  exit 0
else
  err "${FAILURES} échec(s) — rapport : ${REPORT#"${REPO_ROOT}/"}"
  exit 1
fi
