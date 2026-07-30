#!/usr/bin/env python3
"""
SF-01 `preflight` — analyse statique avant compilation.

    python3 software-factory/preflight/run.py [--report] [--strict]

Exécute les contrôles de `checks.py`, regroupe les résultats **par cause
racine** (§19.2) et produit le rapport au format §19.6.

Sortie : 0 si aucune erreur, 1 sinon (utilisable en CI).

Ce composant existe parce que la passe pré-compilation a été refaite
manuellement 3 fois, avec 3 faux positifs à la clé. Voir
`software-factory/ROADMAP.md`.
"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from checks import ALL_CHECKS, Context, Finding  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]

C_RESET, C_RED, C_YEL, C_GRN, C_DIM, C_BOLD = (
    "\033[0m", "\033[31m", "\033[33m", "\033[32m", "\033[2m", "\033[1m")


def group_by_root_cause(findings: list[Finding]) -> dict[str, list[Finding]]:
    """Regroupe par contrôle : un contrôle = une cause racine (§19.2)."""
    groups: dict[str, list[Finding]] = {}
    for f in findings:
        groups.setdefault(f.check, []).append(f)
    return groups


# Ordre de correction : d'abord ce qui empêche de compiler, puis ce qui casse au
# runtime, enfin la qualité. Corriger dans cet ordre évite les recompilations
# inutiles (§19.3).
ORDER = {
    "delimiters": (1, "Bloque la compilation — rien d'autre ne peut être vérifié"),
    "min-sdk": (2, "Compile mais plante à l'exécution sur Android 7"),
    "fileprovider": (3, "Crash au runtime lors du partage"),
    "resources": (4, "Échec de l'étape de packaging des ressources"),
    "global-scope": (5, "Fuite mémoire — violation §5"),
    "room-migration": (6, "Risque de perte de données — exige une validation humaine"),
    "duplicate-res": (7, "Alourdit l'APK, sans blocage"),
    "empty-catch": (8, "Qualité — erreurs silencieuses"),
    "db-in-ui": (9, "Dette d'architecture — planifiée"),
}


def main() -> int:
    ap = argparse.ArgumentParser(description="Analyse statique pré-compilation")
    ap.add_argument("--report", action="store_true",
                    help="écrire un rapport dans .ai/REPORTS/")
    ap.add_argument("--strict", action="store_true",
                    help="échouer aussi sur les avertissements")
    args = ap.parse_args()

    ctx = Context.build(ROOT)
    print(f"{C_BOLD}▸ preflight{C_RESET} — {len(ctx.kt_main)} fichiers source, "
          f"{len(ctx.kt_test)} fichiers de test")

    findings: list[Finding] = []
    for check in ALL_CHECKS:
        try:
            findings += check(ctx)
        except Exception as e:  # un contrôle défaillant ne doit pas tout arrêter
            findings.append(Finding("warning", check.__name__,
                                    f"contrôle en échec : {e}"))

    errors = [f for f in findings if f.severity == "error"]
    warnings = [f for f in findings if f.severity == "warning"]
    groups = group_by_root_cause(findings)

    # ---- affichage
    if not findings:
        print(f"{C_GRN}✅ Aucun problème détecté.{C_RESET}")
    else:
        for check_name in sorted(groups, key=lambda c: ORDER.get(c, (99, ""))[0]):
            items = groups[check_name]
            sev = "error" if any(i.severity == "error" for i in items) else "warning"
            colour = C_RED if sev == "error" else C_YEL
            icon = "❌" if sev == "error" else "⚠️ "
            print(f"\n{colour}{icon} {check_name}{C_RESET} — {len(items)} occurrence(s)")
            for i in items[:10]:
                loc = f"{i.file}:{i.line}" if i.line else i.file
                print(f"   {C_DIM}{loc}{C_RESET}\n     {i.message}")
            if len(items) > 10:
                print(f"   {C_DIM}… et {len(items) - 10} autre(s){C_RESET}")

    print(f"\n{C_BOLD}{'─' * 60}{C_RESET}")
    print(f"Erreurs : {len(errors)} · Avertissements : {len(warnings)} · "
          f"Causes racines : {len(groups)}")

    if args.report:
        write_report(findings, groups, errors, warnings)

    if errors:
        return 1
    if args.strict and warnings:
        return 1
    return 0


def write_report(findings, groups, errors, warnings) -> None:
    stamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    out = ROOT / ".ai" / "REPORTS" / f"preflight_{stamp}.md"
    out.parent.mkdir(parents=True, exist_ok=True)

    # Une cause racine non détectée = au moins un cycle de build perdu.
    saved = len([c for c in groups if ORDER.get(c, (99, ""))[0] <= 4])

    lines = [
        f"# Rapport preflight — {datetime.now().strftime('%Y-%m-%d %H:%M')}",
        "",
        "Analyse statique **avant compilation** (SF-01). Aucun JDK requis.",
        "",
        "| Indicateur | Valeur |",
        "|---|---|",
        f"| Erreurs totales | {len(errors)} |",
        f"| Avertissements | {len(warnings)} |",
        f"| **Causes racines** | **{len(groups)}** |",
        f"| Erreurs dérivées | {max(0, len(findings) - len(groups))} |",
        f"| **Recompilations économisées (est.)** | **{saved}** |",
        "",
    ]

    if groups:
        lines += ["## Ordre de correction recommandé", "",
                  "| Ordre | Cause | Occurrences | Justification |", "|---|---|---|---|"]
        for n, check_name in enumerate(
                sorted(groups, key=lambda c: ORDER.get(c, (99, ""))[0]), 1):
            why = ORDER.get(check_name, (99, "non classé"))[1]
            lines.append(f"| {n} | `{check_name}` | {len(groups[check_name])} | {why} |")
        lines.append("")

        lines += ["## Détail", ""]
        for check_name in sorted(groups, key=lambda c: ORDER.get(c, (99, ""))[0]):
            lines.append(f"### `{check_name}`")
            lines.append("")
            for i in groups[check_name]:
                loc = f"`{i.file}:{i.line}`" if i.line else f"`{i.file}`"
                lines.append(f"- {loc} — {i.message}")
            lines.append("")
    else:
        lines += ["## Résultat", "", "✅ Aucun problème détecté.", ""]

    lines += [
        "---",
        "",
        "> ❓ *Hypothèse* : cette analyse est **statique**. Elle ne remplace pas",
        "> une compilation réelle (`CODING_RULES.md` §13) — elle réduit le nombre",
        "> de cycles nécessaires pour y parvenir.",
    ]
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"{C_DIM}Rapport : {out.relative_to(ROOT)}{C_RESET}")


if __name__ == "__main__":
    sys.exit(main())
