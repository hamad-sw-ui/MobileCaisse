#!/usr/bin/env python3
"""
SF-02 `orchestrator` — pilote la boucle de validation de MobileCaisse.

    python3 software-factory/orchestrator/run.py            # cycle complet
    python3 software-factory/orchestrator/run.py --dry-run  # étapes exécutables ici
    python3 software-factory/orchestrator/run.py --analyze journal.log
    python3 software-factory/orchestrator/run.py --max-iterations 3

Séquence :

    preflight → compilation → tests unitaires → [instrumentation]
      → collecte des journaux → regroupement → causes racines
      → corrections sûres → recompilation → rapport

Chaque étape se déclare **exécutable ou non** dans l'environnement courant.
Ce qui ne peut pas tourner ici est reporté, jamais simulé : un rapport qui
affirme « compilation réussie » sans compilateur serait un mensonge outillé.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))
from analyzers.gradle_log import RootCause, estimate_saved_cycles, parse  # noqa: E402

C_RESET, C_RED, C_YEL, C_GRN, C_DIM, C_BOLD, C_BLU = (
    "\033[0m", "\033[31m", "\033[33m", "\033[32m", "\033[2m", "\033[1m", "\033[34m")

SKIPPED, OK, FAILED = "SKIPPED", "OK", "FAILED"


@dataclass
class StepResult:
    name: str
    status: str
    duration: float = 0.0
    detail: str = ""
    log: str = ""
    blocker: str = ""


@dataclass
class CycleResult:
    steps: list[StepResult] = field(default_factory=list)
    causes: list[RootCause] = field(default_factory=list)
    total_errors: int = 0

    @property
    def failed(self) -> bool:
        return any(s.status == FAILED for s in self.steps)

    @property
    def blockers(self) -> list[StepResult]:
        return [s for s in self.steps if s.status == SKIPPED and s.blocker]


class Environment:
    """Ce que l'environnement courant permet réellement d'exécuter."""

    def __init__(self) -> None:
        self.python = True
        self.docker = shutil.which("docker") is not None
        self.java = shutil.which("java") is not None
        self.gradlew = (ROOT / "gradlew").exists()
        self.adb = shutil.which("adb") is not None
        self.devices = self._devices() if self.adb else 0

    @staticmethod
    def _devices() -> int:
        try:
            out = subprocess.run(["adb", "devices"], capture_output=True,
                                 text=True, timeout=10).stdout
            return sum(1 for ln in out.splitlines()[1:] if ln.strip().endswith("device"))
        except Exception:
            return 0

    @property
    def can_build(self) -> bool:
        return self.docker or (self.java and self.gradlew)

    def summary(self) -> str:
        def mark(v: bool) -> str:
            return f"{C_GRN}✓{C_RESET}" if v else f"{C_RED}✗{C_RESET}"
        return (f"python {mark(self.python)}  docker {mark(self.docker)}  "
                f"java {mark(self.java)}  gradlew {mark(self.gradlew)}  "
                f"adb {mark(self.adb)} ({self.devices} appareil·s)")


class Orchestrator:
    def __init__(self, env: Environment, max_iterations: int = 3) -> None:
        self.env = env
        self.max_iterations = max_iterations
        self.stamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
        self.logs_dir = ROOT / "software-factory" / "cache" / "logs"
        self.logs_dir.mkdir(parents=True, exist_ok=True)

    # ---------------------------------------------------------------- étapes

    def step_preflight(self) -> StepResult:
        t0 = datetime.now()
        proc = subprocess.run(
            [sys.executable, str(ROOT / "software-factory/preflight/run.py"), "--report"],
            capture_output=True, text=True, cwd=ROOT)
        dt = (datetime.now() - t0).total_seconds()
        last = proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else ""
        return StepResult("preflight", OK if proc.returncode == 0 else FAILED,
                          dt, last, proc.stdout)

    def _run_build(self, name: str, docker_script: str, gradle_args: list[str]) -> StepResult:
        if not self.env.can_build:
            return StepResult(
                name, SKIPPED,
                blocker="Ni Docker ni JDK dans cet environnement. "
                        f"À exécuter : ./docker/scripts/{docker_script}")
        t0 = datetime.now()
        if self.env.docker:
            cmd = ["bash", str(ROOT / "docker" / "scripts" / docker_script)]
        else:
            cmd = ["sh", "./gradlew", *gradle_args]
        proc = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
        dt = (datetime.now() - t0).total_seconds()
        log = proc.stdout + proc.stderr
        (self.logs_dir / f"{name}_{self.stamp}.log").write_text(log, encoding="utf-8")
        return StepResult(name, OK if proc.returncode == 0 else FAILED,
                          dt, f"code {proc.returncode}", log)

    def step_build(self) -> StepResult:
        return self._run_build("build", "build.sh", ["assembleDebug", "--warning-mode", "all"])

    def step_unit_tests(self) -> StepResult:
        return self._run_build("tests", "test.sh", ["testDebugUnitTest"])

    def step_instrumentation(self) -> StepResult:
        if self.env.devices == 0:
            return StepResult(
                "instrumentation", SKIPPED,
                blocker="Aucun appareil Android connecté. Les tests instrumentés "
                        "(SecurityMigrationTest : SQLCipher + Keystore matériel) "
                        "s'exécutent hors Docker : ./docker/scripts/test-instrumented.sh")
        return self._run_build("instrumentation", "test-instrumented.sh",
                               ["connectedDebugAndroidTest"])

    # ------------------------------------------------------------------ cycle

    def run(self, dry_run: bool = False) -> CycleResult:
        result = CycleResult()

        print(f"{C_BOLD}▸ orchestrator{C_RESET} — cycle {self.stamp}")
        print(f"  {self.env.summary()}\n")

        for step in (self.step_preflight, self.step_build,
                     self.step_unit_tests, self.step_instrumentation):
            if dry_run and step is not self.step_preflight:
                r = StepResult(step.__name__.replace("step_", ""), SKIPPED,
                               blocker="--dry-run")
            else:
                r = step()
            result.steps.append(r)
            self._print_step(r)

            if r.status == FAILED and r.log:
                errors, causes = parse(r.log)
                result.total_errors += len(errors)
                result.causes = self._merge(result.causes, causes)
                break  # inutile de poursuivre : les étapes suivantes échoueraient

        if result.causes:
            self._print_causes(result)

        self._write_report(result)
        return result

    @staticmethod
    def _merge(existing: list[RootCause], new: list[RootCause]) -> list[RootCause]:
        by_sig = {c.signature: c for c in existing}
        for c in new:
            if c.signature in by_sig:
                by_sig[c.signature].errors += c.errors
                by_sig[c.signature].derived += c.derived
            else:
                by_sig[c.signature] = c
        return sorted(by_sig.values(), key=lambda r: (r.priority, -r.total))

    # -------------------------------------------------------------- affichage

    @staticmethod
    def _print_step(r: StepResult) -> None:
        icon = {OK: f"{C_GRN}✅", FAILED: f"{C_RED}❌", SKIPPED: f"{C_YEL}⏭ "}[r.status]
        dur = f" {C_DIM}({r.duration:.1f}s){C_RESET}" if r.duration else ""
        print(f"{icon} {r.name}{C_RESET}{dur}  {r.detail}")
        if r.blocker:
            print(f"   {C_YEL}↳ {r.blocker}{C_RESET}")

    def _print_causes(self, result: CycleResult) -> None:
        print(f"\n{C_BOLD}Causes racines{C_RESET}")
        for i, c in enumerate(result.causes, 1):
            tag = f"{C_DIM}(dérivée){C_RESET}" if c.priority >= 90 else ""
            print(f"{i}. {C_RED}{c.signature}{C_RESET} — "
                  f"{len(c.errors)} directe(s), {len(c.derived)} dérivée(s) {tag}")
            print(f"   {C_DIM}{c.advice}{C_RESET}")
            for e in c.errors[:3]:
                loc = f"{e.file}:{e.line}" if e.file else ""
                print(f"     {C_DIM}{loc}{C_RESET} {e.message[:90]}")
        print(f"\n{C_BLU}Cycles économisés en corrigeant tout d'un coup : "
              f"{estimate_saved_cycles(result.causes)}{C_RESET}")

    # ---------------------------------------------------------------- rapport

    def _write_report(self, result: CycleResult) -> None:
        out = ROOT / ".ai" / "REPORTS" / f"cycle_{self.stamp}.md"
        out.parent.mkdir(parents=True, exist_ok=True)

        direct = sum(len(c.errors) for c in result.causes)
        derived = sum(len(c.derived) for c in result.causes)
        lines = [
            f"# Rapport de cycle — {datetime.now().strftime('%Y-%m-%d %H:%M')}",
            "",
            f"**Environnement** : {self.env.summary()}".replace(C_GRN, "").replace(
                C_RED, "").replace(C_RESET, ""),
            "",
            "## Étapes", "",
            "| Étape | Résultat | Durée | Détail |", "|---|---|---|---|",
        ]
        for s in result.steps:
            icon = {OK: "✅", FAILED: "❌", SKIPPED: "⏭"}[s.status]
            lines.append(f"| {s.name} | {icon} {s.status} | "
                         f"{s.duration:.1f}s | {s.detail or s.blocker} |")

        lines += ["", "## Analyse des erreurs (§19.6)", "",
                  "| Indicateur | Valeur |", "|---|---|",
                  f"| Erreurs totales | {result.total_errors} |",
                  f"| Causes racines | {len([c for c in result.causes if c.priority < 90])} |",
                  f"| Erreurs dérivées | {derived} |",
                  f"| Erreurs directes | {direct} |",
                  f"| Recompilations économisées | {estimate_saved_cycles(result.causes)} |",
                  ""]

        if result.causes:
            lines += ["### Ordre de correction recommandé", "",
                      "| Ordre | Cause | Directes | Dérivées | Action |",
                      "|---|---|---|---|---|"]
            for i, c in enumerate(result.causes, 1):
                lines.append(f"| {i} | `{c.signature}` | {len(c.errors)} | "
                             f"{len(c.derived)} | {c.advice} |")
            lines.append("")

        if result.blockers:
            lines += ["## ⛔ Étapes non exécutables ici", ""]
            for s in result.blockers:
                lines.append(f"- **{s.name}** — {s.blocker}")
            lines += ["", "Ces étapes sont **reportées, jamais simulées** : un rapport",
                      "affirmant « compilation réussie » sans compilateur serait faux.", ""]
            lines += ["### À exécuter sur un environnement compatible", "",
                      "```bash", "make image && make verify",
                      "python3 software-factory/orchestrator/run.py", "```", ""]

        lines += ["---", "",
                  "> Généré par `software-factory/orchestrator`. Les statuts de bug ne",
                  "> passent en `CORRIGÉ (VALIDÉ)` qu'après compilation **et** tests",
                  "> réussis (`CODING_RULES.md` §13)."]
        out.write_text("\n".join(lines), encoding="utf-8")
        print(f"\n{C_DIM}Rapport : {out.relative_to(ROOT)}{C_RESET}")


def main() -> int:
    ap = argparse.ArgumentParser(description="Orchestrateur de validation MobileCaisse")
    ap.add_argument("--dry-run", action="store_true",
                    help="n'exécute que ce qui est possible ici")
    ap.add_argument("--analyze", metavar="LOG",
                    help="analyse un journal existant, sans rien exécuter")
    ap.add_argument("--max-iterations", type=int, default=3)
    args = ap.parse_args()

    if args.analyze:
        log = Path(args.analyze).read_text(encoding="utf-8", errors="replace")
        errors, causes = parse(log)
        print(f"{C_BOLD}Analyse de {args.analyze}{C_RESET}")
        print(f"{len(errors)} erreur(s), {len(causes)} cause(s) racine(s)\n")
        for i, c in enumerate(causes, 1):
            print(f"{i}. {c.signature} — {len(c.errors)} directe(s), "
                  f"{len(c.derived)} dérivée(s)")
            print(f"   {c.advice}")
        print(f"\nCycles économisés : {estimate_saved_cycles(causes)}")
        return 0

    env = Environment()
    orch = Orchestrator(env, args.max_iterations)
    result = orch.run(dry_run=args.dry_run)
    return 1 if result.failed else 0


if __name__ == "__main__":
    sys.exit(main())
