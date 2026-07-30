#!/usr/bin/env python3
"""
Publie le résultat du dernier cycle dans `software-factory/last-cycle/`.

    python3 software-factory/orchestrator/publish.py <STAMP> [étape_en_échec]

Ce dossier est **versionné volontairement** : c'est le canal de retour vers
l'agent. Sans lui, un cycle exécuté sur la machine du développeur reste local
et la boucle exige un copier-coller manuel du journal.
"""
from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))
from analyzers.gradle_log import parse  # noqa: E402

MAX_LOG_LINES = 2000


def main() -> int:
    stamp = sys.argv[1] if len(sys.argv) > 1 else datetime.now().strftime("%Y-%m-%d_%H%M%S")
    failed = sys.argv[2] if len(sys.argv) > 2 else ""

    out = ROOT / "software-factory" / "last-cycle"
    out.mkdir(parents=True, exist_ok=True)
    logs = ROOT / "software-factory" / "cache" / "logs"

    causes_payload: list[dict] = []
    total_errors = 0

    if logs.exists():
        for log in sorted(logs.glob(f"*_{stamp}.log")):
            text = log.read_text(encoding="utf-8", errors="replace")
            # Journal tronqué : suffisant pour diagnostiquer, sans alourdir le dépôt.
            (out / f"{log.stem.rsplit('_', 2)[0]}.log").write_text(
                "\n".join(text.splitlines()[-MAX_LOG_LINES:]), encoding="utf-8")
            errors, causes = parse(text)
            total_errors += len(errors)
            for c in causes:
                causes_payload.append({
                    "signature": c.signature,
                    "priority": c.priority,
                    "direct": len(c.errors),
                    "derived": len(c.derived),
                    "advice": c.advice,
                    "samples": [f"{e.file}:{e.line} {e.message}"[:200] for e in c.errors[:3]],
                })

    causes_payload.sort(key=lambda c: c["priority"])
    independent = [c for c in causes_payload if c["priority"] < 90]

    try:
        commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                                capture_output=True, text=True, cwd=ROOT).stdout.strip()
    except Exception:
        commit = "inconnu"

    status = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "stamp": stamp,
        "success": failed == "",
        "failed_step": failed or None,
        "commit": commit,
        "total_errors": total_errors,
        "root_causes": causes_payload,
        "saved_cycles": max(0, len(independent) - 1),
    }
    (out / "status.json").write_text(
        json.dumps(status, indent=2, ensure_ascii=False), encoding="utf-8")

    verdict = "✅ RÉUSSI" if status["success"] else f"❌ ÉCHEC — étape « {failed} »"
    md = [
        f"# Dernier cycle — {status['timestamp']}",
        "",
        f"**Commit** : `{commit}` · **Résultat** : {verdict}",
        f"**Erreurs** : {total_errors} · **Causes racines** : {len(independent)} · "
        f"**Cycles économisés** : {status['saved_cycles']}",
        "",
    ]
    if causes_payload:
        md += ["## Ordre de correction recommandé", "",
               "| # | Cause | Directes | Dérivées | Action |", "|---|---|---|---|---|"]
        for i, c in enumerate(causes_payload, 1):
            md.append(f"| {i} | `{c['signature']}` | {c['direct']} | "
                      f"{c['derived']} | {c['advice']} |")
        md += ["", "## Extraits", ""]
        for c in causes_payload:
            for s in c["samples"]:
                md.append(f"- `{s}`")
    else:
        md.append("Aucune erreur détectée dans les journaux de ce cycle.")

    md += ["", "---", "",
           "> Généré par `software-factory/orchestrator/publish.py`.",
           "> Ce dossier est versionné : il permet à l'agent de reprendre le",
           "> diagnostic sans copier-coller de journal."]
    (out / "summary.md").write_text("\n".join(md), encoding="utf-8")

    print(f"  Résultat publié : software-factory/last-cycle/ "
          f"({total_errors} erreur·s, {len(independent)} cause·s racine·s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
