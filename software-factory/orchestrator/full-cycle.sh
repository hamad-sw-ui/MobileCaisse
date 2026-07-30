#!/usr/bin/env bash
###############################################################################
# Lanceur — délègue tout à l'Execution Engine.
#
#   ./software-factory/orchestrator/full-cycle.sh [options]
#
# Toute la logique vit dans software-factory/engine/. Ce script ne fait que
# vérifier Python, invoquer le moteur, puis committer le résultat.
#
# Options transmises telles quelles :
#   --resume · --status · --max-loops N · --no-emulator · --no-autofix · --skip-instr
###############################################################################
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/software-factory"

if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 requis pour la Software Factory." >&2
  exit 1
fi

python3 -m engine.engine "$@"
STATUS=$?

# Publication du résultat vers l'agent : sans ce commit, un cycle exécuté ici
# resterait local et exigerait un copier-coller du journal.
cd "$ROOT"
if [[ -d .git ]] && command -v git >/dev/null 2>&1; then
  git add software-factory/last-cycle 2>/dev/null || true
  if ! git diff --cached --quiet -- software-factory/last-cycle 2>/dev/null; then
    if git -c user.name="Software Factory" -c user.email="factory@mobilecaisse" \
         commit -q -m "ci(cycle): $(date +%Y-%m-%d_%H%M%S)" \
         -- software-factory/last-cycle 2>/dev/null; then
      echo -e "\033[2m   Résultat committé — git push pour le transmettre à l'agent\033[0m"
    fi
  fi
fi

exit $STATUS
