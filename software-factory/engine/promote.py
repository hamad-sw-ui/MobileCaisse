#!/usr/bin/env python3
"""
Promotion automatique des statuts de bug après un cycle vert.

**Intervention humaine supprimée** : après chaque cycle réussi, relire
`.ai/BUGS.md`, décider quels bugs peuvent passer de `CORRIGÉ (INSPECTION)` à
`CORRIGÉ (VALIDÉ)`, éditer chaque entrée à la main. Mesuré : 9 commits sur
`BUGS.md` en 12 sessions, ~10 min par passage sur 13 bugs — environ 90 minutes.

C'est aussi l'étape la plus sujette à l'erreur humaine : j'ai moi-même écrit
« CORRIGÉ » sur des bugs jamais compilés, ce qui a motivé la règle §13.

Règle appliquée (`CODING_RULES.md` §13) — un bug ne passe en `CORRIGÉ (VALIDÉ)`
que si les preuves **réellement obtenues** couvrent ce qu'il exige :

| Exigence du bug        | Preuve nécessaire                    |
|------------------------|--------------------------------------|
| compilation            | étape `build` verte                  |
| comportement testé     | étape `tests` verte                  |
| SQLCipher / Keystore   | étape `instrumentation` verte        |
| compatibilité API 24   | instrumentation **sur appareil API 24** |

Sans la preuve exigée, le statut reste inchangé — jamais de promotion optimiste.
"""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUGS = ROOT / ".ai" / "BUGS.md"

INSPECTION = "CORRIGÉ (INSPECTION)"
VALIDATED = "CORRIGÉ (VALIDÉ)"


@dataclass
class Proof:
    """Ce que le cycle a réellement démontré."""
    build: bool = False
    tests: bool = False
    instrumentation: bool = False
    device_api: int | None = None       # API du plus ancien appareil testé

    @property
    def any(self) -> bool:
        return self.build or self.tests or self.instrumentation


@dataclass
class Decision:
    bug: str
    promoted: bool
    reason: str


# Bugs exigeant une preuve plus forte que la simple compilation.
# Clé : identifiant ; valeur : (exigence, explication affichée).
SPECIAL_REQUIREMENTS: dict[str, tuple[str, str]] = {
    "BUG-020": ("device_api_24",
                "compatibilité minSdk 24 — exige une exécution sur appareil API 24"),
    "BUG-011": ("instrumentation",
                "checkpoint WAL — vérifiable seulement sur base SQLCipher réelle"),
    "BUG-024": ("instrumentation",
                "concurrence à la restauration — exige une base réelle"),
    "BUG-022": ("instrumentation",
                "migration staff.pinSalt — exige un test de migration Room"),
}


def parse_bugs(text: str) -> list[tuple[str, int]]:
    """Retourne (identifiant, index du bloc de statut) pour chaque bug inspecté."""
    found = []
    for m in re.finditer(r'^## .*?(BUG-\d+)', text, re.M):
        bug = m.group(1)
        # Le statut suit immédiatement le titre.
        window = text[m.end():m.end() + 400]
        if INSPECTION in window:
            found.append((bug, m.end() + window.index(INSPECTION)))
    return found


def evaluate(bug: str, proof: Proof) -> Decision:
    requirement = SPECIAL_REQUIREMENTS.get(bug)

    if requirement:
        kind, explanation = requirement
        if kind == "device_api_24":
            if proof.device_api is not None and proof.device_api <= 24:
                return Decision(bug, True, f"instrumentation sur appareil API {proof.device_api}")
            if proof.instrumentation:
                api = proof.device_api or "?"
                return Decision(bug, False,
                                f"instrumentation OK mais sur API {api} — {explanation}")
            return Decision(bug, False, explanation)
        if kind == "instrumentation":
            if proof.instrumentation:
                return Decision(bug, True, "instrumentation verte")
            return Decision(bug, False, explanation)

    # Cas général : compilation + tests unitaires suffisent.
    if proof.build and proof.tests:
        return Decision(bug, True, "compilation et tests unitaires verts")
    if proof.build:
        return Decision(bug, False, "compilation verte mais tests non exécutés")
    return Decision(bug, False, "compilation non prouvée")


def promote(proof: Proof, apply: bool = False) -> list[Decision]:
    if not BUGS.exists():
        print("BUGS.md introuvable.")
        return []

    text = BUGS.read_text(encoding="utf-8")
    decisions = [evaluate(bug, proof) for bug, _ in parse_bugs(text)]

    if apply:
        stamp = datetime.now().strftime("%Y-%m-%d")
        # Traitement de bas en haut : les remplacements ne décalent pas les
        # positions restantes.
        for bug, pos in reversed(parse_bugs(text)):
            decision = next((d for d in decisions if d.bug == bug), None)
            if not decision or not decision.promoted:
                continue
            end = pos + len(INSPECTION)
            text = text[:pos] + VALIDATED + text[end:]
            # Retirer la mention d'attente devenue fausse.
            tail_start = pos
            tail = text[tail_start:tail_start + 500]
            cleaned = re.sub(
                r'\n\*\*⏳ En attente de validation par compilation réelle\.\*\*',
                f"\n**Validé le {stamp}** — {decision.reason}.", tail, count=1)
            text = text[:tail_start] + cleaned + text[tail_start + len(tail):]
        BUGS.write_text(text, encoding="utf-8")

    return decisions


def main() -> int:
    import argparse
    sys.path.insert(0, str(ROOT / "software-factory"))
    from engine.state import PipelineState, Status  # noqa: E402

    ap = argparse.ArgumentParser(description="Promotion des statuts de bug")
    ap.add_argument("--apply", action="store_true", help="modifie réellement BUGS.md")
    args = ap.parse_args()

    state = PipelineState.load()
    proof = Proof()
    if state:
        proof.build = state.step("build").status == Status.OK
        proof.tests = state.step("tests").status == Status.OK
        proof.instrumentation = state.step("instrumentation").status == Status.OK

    if proof.instrumentation:
        # L'API réelle de l'appareil détermine ce que l'instrumentation prouve.
        try:
            from environment.detect import detect
            env = detect()
            apis = [int(d.api) for d in env.devices if d.state == "device" and d.api.isdigit()]
            proof.device_api = min(apis) if apis else None
        except Exception:
            proof.device_api = None

    G, R, Y, D, B, X = ("\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[1m", "\033[0m")
    print(f"{B}▸ Promotion des statuts{X}")
    print(f"  Preuves : build={proof.build} tests={proof.tests} "
          f"instrumentation={proof.instrumentation}"
          f"{f' (API {proof.device_api})' if proof.device_api else ''}")

    if not proof.any:
        print(f"  {Y}Aucune preuve d'exécution — aucun statut modifié.{X}")
        return 0

    decisions = promote(proof, apply=args.apply)
    if not decisions:
        print(f"  {D}Aucun bug en {INSPECTION}.{X}")
        return 0

    promoted = [d for d in decisions if d.promoted]
    blocked = [d for d in decisions if not d.promoted]

    for d in promoted:
        print(f"  {G}✓{X} {d.bug} → {VALIDATED}  {D}{d.reason}{X}")
    for d in blocked:
        print(f"  {Y}·{X} {d.bug} reste en {INSPECTION}  {D}{d.reason}{X}")

    print(f"\n  {len(promoted)} promu·s, {len(blocked)} maintenu·s")
    if not args.apply and promoted:
        print(f"  {D}Simulation — utiliser --apply pour écrire dans BUGS.md{X}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
