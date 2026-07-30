#!/usr/bin/env bash
###############################################################################
# Cycle de validation complet — à exécuter sur un environnement outillé.
#
#   ./software-factory/orchestrator/full-cycle.sh
#
# Enchaîne sans intervention :
#   preflight → image Docker → environnement → compilation → tests unitaires
#   → [instrumentation si appareil] → analyse par causes racines → rapports
#
# Tout est écrit dans .ai/REPORTS/. En cas d'échec, le rapport de cycle
# contient l'ordre de correction recommandé (§19.6).
#
# Aucun prérequis hors Docker : ni JDK, ni SDK Android sur la machine hôte.
###############################################################################
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

STAMP="$(date +%Y-%m-%d_%H%M%S)"
LOGS="software-factory/cache/logs"
mkdir -p "$LOGS" .ai/REPORTS

c_grn=$'\033[32m'; c_red=$'\033[31m'; c_yel=$'\033[33m'
c_bold=$'\033[1m'; c_dim=$'\033[2m'; c_off=$'\033[0m'

step()  { echo; echo "${c_bold}━━━ $* ━━━${c_off}"; }
ok()    { echo "${c_grn}✅ $*${c_off}"; }
warn()  { echo "${c_yel}⚠️  $*${c_off}"; }
fail()  { echo "${c_red}❌ $*${c_off}"; }

FAILED_STEP=""

# ---------------------------------------------------------------- 1. preflight
step "1/7 · preflight (analyse statique)"
if python3 software-factory/preflight/run.py --report; then
  ok "preflight : aucune erreur bloquante"
else
  fail "preflight a détecté des erreurs — corriger avant de compiler"
  echo "${c_dim}Poursuite quand même : la compilation confirmera.${c_off}"
fi

# ------------------------------------------------------------------ 2. Docker
step "2/7 · Environnement Docker"
if ! command -v docker >/dev/null 2>&1; then
  fail "Docker introuvable. Installer Docker Desktop ou Docker Engine."
  exit 1
fi
if ! docker image inspect mobilecaisse/android-build:1.0.0 >/dev/null 2>&1; then
  warn "Image absente — construction (~5-10 min la première fois)…"
  ./docker/scripts/build-image.sh || { fail "Construction de l'image échouée"; exit 1; }
fi
./docker/scripts/verify-env.sh || { fail "Environnement invalide"; exit 1; }
ok "Environnement validé"

# ------------------------------------------------------------- 3. compilation
step "3/7 · Compilation"
BUILD_LOG="$LOGS/build_${STAMP}.log"
if ./docker/scripts/build.sh debug 2>&1 | tee "$BUILD_LOG"; then
  ok "Compilation réussie"
else
  fail "Compilation échouée"
  FAILED_STEP="build"
fi

# ------------------------------------------------------- 4. tests unitaires
if [[ -z "$FAILED_STEP" ]]; then
  step "4/7 · Tests unitaires"
  TEST_LOG="$LOGS/tests_${STAMP}.log"
  if ./docker/scripts/test.sh 2>&1 | tee "$TEST_LOG"; then
    ok "Tests unitaires réussis"
  else
    fail "Tests unitaires en échec"
    FAILED_STEP="tests"
  fi
else
  step "4/7 · Tests unitaires — ignorés (compilation en échec)"
fi

# ---------------------------------------------------- 5. instrumentation
step "5/7 · Tests d'instrumentation"
if [[ -n "$FAILED_STEP" ]]; then
  warn "Ignorés : une étape précédente a échoué"
elif ! command -v adb >/dev/null 2>&1; then
  warn "adb absent — instrumentation ignorée"
elif [[ "$(adb devices | grep -cw device)" -eq 0 ]]; then
  warn "Aucun appareil connecté — instrumentation ignorée"
  echo "${c_dim}   SecurityMigrationTest exige un Keystore matériel : brancher un${c_off}"
  echo "${c_dim}   téléphone ou lancer un émulateur, puis relancer ce script.${c_off}"
else
  INSTR_LOG="$LOGS/instrumentation_${STAMP}.log"
  if ./docker/scripts/test-instrumented.sh 2>&1 | tee "$INSTR_LOG"; then
    ok "Tests instrumentés réussis"
  else
    fail "Tests instrumentés en échec"
    FAILED_STEP="${FAILED_STEP:-instrumentation}"
  fi
fi

# ------------------------------------------------------------- 6. analyse
step "6/7 · Analyse des journaux"
for log in "$LOGS"/*_"${STAMP}".log; do
  [[ -f "$log" ]] || continue
  echo "${c_dim}— $(basename "$log")${c_off}"
  python3 software-factory/orchestrator/run.py --analyze "$log"
done

# ------------------------------------------- 7. canal de retour vers l'agent
step "7/7 · Publication du résultat"
python3 software-factory/orchestrator/publish.py "$STAMP" "${FAILED_STEP:-}"

if [[ -d .git ]] && command -v git >/dev/null 2>&1; then
  git add software-factory/last-cycle 2>/dev/null || true
  if ! git diff --cached --quiet -- software-factory/last-cycle 2>/dev/null; then
    MSG="ci(cycle): resultat ${STAMP} - ${FAILED_STEP:-succes}"
    if git -c user.name="Software Factory" -c user.email="factory@mobilecaisse" \
           commit -q -m "$MSG" -- software-factory/last-cycle 2>/dev/null; then
      ok "Résultat committé"
      echo "${c_dim}   Pousser pour que l'agent le récupère : git push${c_off}"
    fi
  fi
fi

echo
if [[ -z "$FAILED_STEP" ]]; then
  echo "${c_grn}${c_bold}═══ CYCLE RÉUSSI ═══${c_off}"
  echo
  echo "Les bugs en CORRIGÉ (INSPECTION) peuvent passer en CORRIGÉ (VALIDÉ)."
  echo "Rapports : .ai/REPORTS/"
  exit 0
else
  echo "${c_red}${c_bold}═══ CYCLE EN ÉCHEC — étape « ${FAILED_STEP} » ═══${c_off}"
  echo
  echo "L'ordre de correction recommandé figure ci-dessus (causes racines d'abord)."
  echo "Journaux complets : $LOGS/"
  exit 1
fi
