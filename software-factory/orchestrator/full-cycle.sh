#!/usr/bin/env bash
###############################################################################
# Pipeline de validation MobileCaisse — exécution autonome.
#
#   ./software-factory/orchestrator/full-cycle.sh [options]
#
#   --no-emulator   ne pas démarrer d'émulateur
#   --no-autofix    ne pas appliquer les corrections sûres
#   --skip-instr    ignorer les tests d'instrumentation
#
# Détecte l'environnement, choisit sa stratégie (Docker ou JDK local), compile,
# teste, analyse les causes racines, applique les corrections sûres, relance,
# publie le résultat dans software-factory/last-cycle/.
#
# Aucune intervention humaine hors « git push » final.
###############################################################################
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

STAMP="$(date +%Y-%m-%d_%H%M%S)"
LOGS="software-factory/cache/logs"
mkdir -p "$LOGS" .ai/REPORTS

WITH_EMULATOR=1; WITH_AUTOFIX=1; WITH_INSTR=1
for arg in "$@"; do
  case "$arg" in
    --no-emulator) WITH_EMULATOR=0 ;;
    --no-autofix)  WITH_AUTOFIX=0 ;;
    --skip-instr)  WITH_INSTR=0 ;;
  esac
done

c_grn=$'\033[32m'; c_red=$'\033[31m'; c_yel=$'\033[33m'
c_bold=$'\033[1m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
step() { echo; echo "${c_bold}━━━ $* ━━━${c_off}"; }
ok()   { echo "${c_grn}✅ $*${c_off}"; }
warn() { echo "${c_yel}⚠️  $*${c_off}"; }
fail() { echo "${c_red}❌ $*${c_off}"; }

FAILED_STEP=""

# ------------------------------------------------------- 1. environnement
step "1/8 · Détection de l'environnement"
python3 software-factory/environment/detect.py
eval "$(python3 software-factory/environment/detect.py --export)"

case "${SF_STRATEGY:-none}" in
  docker) ok "Stratégie : Docker (reproductible)" ;;
  local)  warn "Stratégie : JDK local — Docker indisponible, repli sur Gradle" ;;
  *)      fail "Aucune stratégie de compilation disponible."
          echo "  Corriger les points bloquants ci-dessus, puis relancer."
          python3 software-factory/orchestrator/publish.py "$STAMP" "environnement"
          exit 1 ;;
esac

# Le wrapper est invoqué par « sh gradlew » : fonctionne même sans bit +x.
run_gradle() {  # $1 = nom, $2.. = tâches
  local name="$1"; shift
  local log="$LOGS/${name}_${STAMP}.log"
  if [[ "${SF_STRATEGY}" == "docker" ]]; then
    case "$name" in
      build) ./docker/scripts/build.sh debug 2>&1 | tee "$log" ;;
      tests) ./docker/scripts/test.sh          2>&1 | tee "$log" ;;
      *)     ./docker/scripts/build.sh debug   2>&1 | tee "$log" ;;
    esac
  else
    JAVA_HOME="$(dirname "$(dirname "${SF_JDK}")")" \
    ANDROID_HOME="${SF_SDK}" \
      sh ./gradlew "$@" 2>&1 | tee "$log"
  fi
  return "${PIPESTATUS[0]}"
}

# ---------------------------------------------------------- 2. preflight
step "2/8 · Analyse statique (preflight)"
python3 software-factory/preflight/run.py --report \
  || warn "preflight signale des problèmes — la compilation confirmera"

# ------------------------------------------------------------ 3. autofix
if [[ $WITH_AUTOFIX -eq 1 ]]; then
  step "3/8 · Corrections automatiques sûres"
  python3 software-factory/autofix/run.py --apply
else
  step "3/8 · Corrections automatiques — ignorées (--no-autofix)"
fi

# -------------------------------------------------------- 4. compilation
step "4/8 · Compilation"
if run_gradle build assembleDebug --warning-mode all; then
  ok "Compilation réussie"
else
  fail "Compilation échouée"
  FAILED_STEP="build"
fi

# ----------------------------------------------------- 5. tests unitaires
if [[ -z "$FAILED_STEP" ]]; then
  step "5/8 · Tests unitaires"
  if run_gradle tests testDebugUnitTest; then
    ok "Tests unitaires réussis"
  else
    fail "Tests unitaires en échec"
    FAILED_STEP="tests"
  fi
else
  step "5/8 · Tests unitaires — ignorés (compilation en échec)"
fi

# ------------------------------------------------- 6. instrumentation
step "6/8 · Tests d'instrumentation"
if [[ -n "$FAILED_STEP" ]]; then
  warn "Ignorés : une étape précédente a échoué"
elif [[ $WITH_INSTR -eq 0 ]]; then
  warn "Ignorés (--skip-instr)"
elif [[ -z "${SF_ADB:-}" ]]; then
  warn "adb indisponible — instrumentation impossible"
else
  if [[ "${SF_DEVICES:-0}" -eq 0 && $WITH_EMULATOR -eq 1 ]]; then
    python3 software-factory/environment/emulator.py --ensure
    eval "$(python3 software-factory/environment/detect.py --export)"
  fi
  if [[ "${SF_DEVICES:-0}" -gt 0 ]]; then
    INSTR_LOG="$LOGS/instrumentation_${STAMP}.log"
    if JAVA_HOME="$(dirname "$(dirname "${SF_JDK}")")" ANDROID_HOME="${SF_SDK}" \
         sh ./gradlew connectedDebugAndroidTest 2>&1 | tee "$INSTR_LOG"; then
      ok "Tests instrumentés réussis"
    else
      fail "Tests instrumentés en échec"
      FAILED_STEP="${FAILED_STEP:-instrumentation}"
    fi
  else
    warn "Aucun appareil disponible — instrumentation ignorée"
  fi
fi

# ------------------------------------------------------------- 7. analyse
step "7/8 · Analyse des journaux"
for log in "$LOGS"/*_"${STAMP}".log; do
  [[ -f "$log" ]] || continue
  echo "${c_dim}— $(basename "$log")${c_off}"
  python3 software-factory/orchestrator/run.py --analyze "$log"
done

# --------------------------------------------------------- 8. publication
step "8/8 · Publication du résultat"
python3 software-factory/orchestrator/publish.py "$STAMP" "${FAILED_STEP:-}"

if [[ -d .git ]] && command -v git >/dev/null 2>&1; then
  git add software-factory/last-cycle 2>/dev/null || true
  if ! git diff --cached --quiet -- software-factory/last-cycle 2>/dev/null; then
    if git -c user.name="Software Factory" -c user.email="factory@mobilecaisse" \
         commit -q -m "ci(cycle): ${STAMP} - ${FAILED_STEP:-succes}" \
         -- software-factory/last-cycle 2>/dev/null; then
      ok "Résultat committé"
      echo "${c_dim}   git push  → l'agent récupère le diagnostic${c_off}"
    fi
  fi
fi

echo
if [[ -z "$FAILED_STEP" ]]; then
  echo "${c_grn}${c_bold}═══ CYCLE RÉUSSI ═══${c_off}"
  echo "Les bugs CORRIGÉ (INSPECTION) peuvent passer en CORRIGÉ (VALIDÉ)."
  exit 0
else
  echo "${c_red}${c_bold}═══ ÉCHEC — étape « ${FAILED_STEP} » ═══${c_off}"
  echo "Ordre de correction : software-factory/last-cycle/summary.md"
  exit 1
fi
