#!/usr/bin/env python3
"""
SF-03 `autofix` — applique les corrections sûres, propose les autres.

    python3 software-factory/autofix/run.py            # simulation (défaut)
    python3 software-factory/autofix/run.py --apply    # applique réellement
    python3 software-factory/autofix/run.py --apply --rule fileprovider

Par défaut **rien n'est modifié** : il faut `--apply` explicitement. Une
correction automatique silencieuse serait plus dangereuse que le défaut
qu'elle corrige.
"""
from __future__ import annotations

import argparse
import re
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))
from autofix.rules import (  # noqa: E402
    AUTO_APPLICABLE, PROPOSE_ONLY, Fix, fix_empty_catch,
    fix_fileprovider_authority, fix_trailing_whitespace, fix_unused_imports,
    is_protected,
)

C_RESET, C_RED, C_YEL, C_GRN, C_DIM, C_BOLD = (
    "\033[0m", "\033[31m", "\033[33m", "\033[32m", "\033[2m", "\033[1m")


def manifest_authority() -> str | None:
    mf = ROOT / "app/src/main/AndroidManifest.xml"
    if not mf.exists():
        return None
    m = re.search(r'android:authorities="\$\{applicationId\}\.([\w.]+)"',
                  mf.read_text(encoding="utf-8", errors="replace"))
    return m.group(1) if m else None


def collect() -> list[Fix]:
    authority = manifest_authority()
    fixes: list[Fix] = []
    for path in sorted((ROOT / "app/src/main").rglob("*.kt")):
        src = path.read_text(encoding="utf-8", errors="replace")
        rel = str(path.relative_to(ROOT))
        if authority:
            fixes += fix_fileprovider_authority(Path(rel), src, authority)
        fixes += fix_empty_catch(Path(rel), src)
        fixes += fix_unused_imports(Path(rel), src)
        fixes += fix_trailing_whitespace(Path(rel), src)
    return fixes


def apply_fix(fix: Fix) -> bool:
    """Applique une correction en préservant le reste du fichier à l'identique."""
    path = ROOT / fix.file
    lines = path.read_text(encoding="utf-8", errors="replace").split("\n")
    idx = fix.line - 1
    if idx < 0 or idx >= len(lines):
        return False

    if fix.rule == "trailing-space":
        if lines[idx] != fix.before:
            return False
        lines[idx] = fix.after
    elif fix.rule == "unused-import":
        if lines[idx].strip() != fix.before.strip():
            return False
        del lines[idx]
    elif fix.rule == "fileprovider":
        if fix.before not in lines[idx]:
            return False
        lines[idx] = lines[idx].replace(fix.before, fix.after)
    else:
        return False

    path.write_text("\n".join(lines), encoding="utf-8")
    return True


def main() -> int:
    ap = argparse.ArgumentParser(description="Corrections automatiques sûres")
    ap.add_argument("--apply", action="store_true", help="applique réellement")
    ap.add_argument("--rule", help="restreindre à une règle")
    args = ap.parse_args()

    fixes = collect()
    if args.rule:
        fixes = [f for f in fixes if f.rule == args.rule]

    protected = [f for f in fixes if is_protected(f.file)]
    fixes = [f for f in fixes if not is_protected(f.file)]

    auto = [f for f in fixes if f.rule in AUTO_APPLICABLE]
    manual = [f for f in fixes if f.rule in PROPOSE_ONLY]

    mode = "APPLICATION" if args.apply else "SIMULATION"
    print(f"{C_BOLD}▸ autofix{C_RESET} — mode {mode}")
    print(f"  {len(auto)} correction(s) sûre(s) · {len(manual)} à traiter "
          f"manuellement · {len(protected)} en zone protégée\n")

    by_rule: dict[str, list[Fix]] = {}
    for f in auto:
        by_rule.setdefault(f.rule, []).append(f)

    applied = 0
    for rule, items in sorted(by_rule.items()):
        print(f"{C_GRN}▪ {rule}{C_RESET} — {len(items)} occurrence(s)")
        # Appliquer de bas en haut : supprimer une ligne décale les suivantes.
        for f in sorted(items, key=lambda x: (x.file, -x.line)):
            if args.apply:
                if apply_fix(f):
                    f.applied = True
                    applied += 1
            if len([i for i in items if i.file == f.file]) <= 3 or not args.apply:
                mark = "✓" if f.applied else " "
                print(f"   {mark} {C_DIM}{f.file}:{f.line}{C_RESET} {f.explanation}")

    if manual:
        print(f"\n{C_YEL}▪ à traiter manuellement{C_RESET} — {len(manual)}")
        for f in manual:
            print(f"     {C_DIM}{f.file}:{f.line}{C_RESET} {f.explanation}")

    if protected:
        print(f"\n{C_RED}▪ zone protégée — aucune correction automatique{C_RESET}")
        seen = set()
        for f in protected:
            if f.file not in seen:
                seen.add(f.file)
                print(f"     {C_DIM}{f.file}{C_RESET}")
        print(f"     {C_DIM}crypto, migrations, entités : validation humaine "
              f"obligatoire (§13){C_RESET}")

    print(f"\n{C_BOLD}{'─' * 58}{C_RESET}")
    if args.apply:
        print(f"{applied} correction(s) appliquée(s).")
        if applied:
            print(f"{C_YEL}Relancer preflight puis compiler pour valider.{C_RESET}")
    else:
        print(f"Simulation. Utiliser {C_BOLD}--apply{C_RESET} pour appliquer.")

    if args.apply and applied:
        report = ROOT / ".ai/REPORTS" / f"autofix_{datetime.now():%Y-%m-%d_%H%M%S}.md"
        report.parent.mkdir(parents=True, exist_ok=True)
        lines = [f"# Corrections automatiques — {datetime.now():%Y-%m-%d %H:%M}", "",
                 f"**{applied} correction(s) appliquée(s)**", "",
                 "| Fichier | Ligne | Règle | Correction |", "|---|---|---|---|"]
        for f in auto:
            if f.applied:
                lines.append(f"| `{f.file}` | {f.line} | `{f.rule}` | {f.explanation} |")
        if manual:
            lines += ["", "## Non automatisable", ""]
            for f in manual:
                lines.append(f"- `{f.file}:{f.line}` — {f.explanation}")
        report.write_text("\n".join(lines), encoding="utf-8")
        print(f"{C_DIM}Rapport : {report.relative_to(ROOT)}{C_RESET}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
