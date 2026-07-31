#!/usr/bin/env python3
"""
Execution Engine — point d'entrée unique de la Software Factory.

    python3 -m engine.engine                 # cycle complet
    python3 -m engine.engine --resume        # reprend après un échec
    python3 -m engine.engine --status        # état du dernier cycle
    python3 -m engine.engine --max-loops 3   # boucles corriger→relancer
    python3 -m engine.engine --skip-instr --no-autofix

Responsabilités concentrées ici :
  · découverte de l'environnement · choix de stratégie · orchestration des
    runners · relance des étapes réessayables · reprise d'un cycle interrompu
  · boucle corriger→revalider · état unique du pipeline

`full-cycle.sh` n'est plus qu'un lanceur.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))

from analyzers.gradle_log import RootCause, estimate_saved_cycles, parse  # noqa: E402
from engine.runners import PIPELINE, RunContext, Runner  # noqa: E402
from engine.state import PipelineState, Status  # noqa: E402
from environment.detect import detect  # noqa: E402

G, R, Y, D, B, X = ("\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[1m", "\033[0m")
ICON = {Status.OK: f"{G}✅{X}", Status.FAILED: f"{R}❌{X}",
        Status.SKIPPED: f"{Y}⏭ {X}", Status.PENDING: f"{D}·{X}",
        Status.RUNNING: f"{B}▸{X}"}

MAX_RETRIES = 1        # une seule relance : au-delà, l'échec est structurel


class ExecutionEngine:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.logs = ROOT / "software-factory" / "cache" / "logs"
        self.logs.mkdir(parents=True, exist_ok=True)
        self.commit = self._commit()
        self.state = self._init_state()
        self.env = None

    # ------------------------------------------------------------ amorçage

    @staticmethod
    def _commit() -> str:
        try:
            return subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                                  capture_output=True, text=True,
                                  cwd=ROOT).stdout.strip() or "inconnu"
        except Exception:
            return "inconnu"

    def _init_state(self) -> PipelineState:
        if self.args.resume:
            previous = PipelineState.load()
            if previous and previous.resumable_from(self.commit):
                print(f"{B}▸ Reprise{X} du cycle {previous.stamp} "
                      f"(échec à « {previous.failed_step} »)")
                done = [s.name for s in previous.steps.values() if s.done]
                if done:
                    print(f"  {D}Étapes déjà validées, non rejouées : "
                          f"{', '.join(done)}{X}")
                return previous
            if previous and previous.commit != self.commit:
                print(f"{Y}Le code a changé depuis le dernier cycle "
                      f"({previous.commit} → {self.commit}) : reprise impossible, "
                      f"cycle complet.{X}")
        return PipelineState.fresh(self.commit)

    # ------------------------------------------------------------ exécution

    def discover(self) -> None:
        print(f"{B}▸ Découverte de l'environnement{X}")
        self.env = detect()
        self.state.strategy = self.env.strategy

        def mk(t) -> str:
            return f"{G}✓{X}" if t.usable else (f"{Y}~{X}" if t.found else f"{R}✗{X}")

        print(f"  {mk(self.env.docker)} docker   {mk(self.env.jdk)} jdk   "
              f"{mk(self.env.sdk)} sdk   {mk(self.env.adb)} adb   "
              f"{mk(self.env.emulator)} emulator")
        ready = [d for d in self.env.devices if d.state == "device"]
        label = {"docker": f"{G}Docker{X}", "local": f"{Y}JDK local{X}"}.get(
            self.env.strategy, f"{R}aucune{X}")
        print(f"  Stratégie : {label}"
              f"{f' · {len(ready)} appareil·s' if ready else ''}")
        if self.env.blockers:
            for b in self.env.blockers:
                print(f"  {R}•{X} {b}")

    def run_step(self, runner: Runner, ctx: RunContext) -> Status:
        step = self.state.step(runner.name)

        if step.done and self.args.resume:
            print(f"{ICON[Status(step.status)]} {runner.name} "
                  f"{D}(déjà validé, ignoré){X}")
            return Status(step.status)

        applicable, reason = runner.applicable(ctx)
        if not applicable:
            step.status, step.blocker = Status.SKIPPED, reason
            self.state.save()
            print(f"{ICON[Status.SKIPPED]} {runner.name}  {Y}{reason}{X}")
            return Status.SKIPPED

        for attempt in range(MAX_RETRIES + 1):
            step.status = Status.RUNNING
            step.started = datetime.now().isoformat(timespec="seconds")
            step.attempts = attempt + 1
            self.state.save()

            t0 = datetime.now()
            code = runner.execute(ctx, step)
            step.duration = (datetime.now() - t0).total_seconds()
            step.finished = datetime.now().isoformat(timespec="seconds")
            step.exit_code = code

            if code == 0:
                step.status = Status.OK
                self.state.save()
                print(f"{ICON[Status.OK]} {runner.name} "
                      f"{D}({step.duration:.0f}s){X}  {step.detail}")
                return Status.OK

            if runner.retryable and attempt < MAX_RETRIES:
                print(f"{Y}↻{X} {runner.name} — échec (code {code}), "
                      f"nouvelle tentative…")
                continue
            break

        step.status = Status.FAILED
        self.state.save()
        print(f"{ICON[Status.FAILED]} {runner.name} "
              f"{D}({step.duration:.0f}s){X}  {step.detail}")
        return Status.FAILED

    def analyze(self) -> list[RootCause]:
        causes: dict[str, RootCause] = {}
        for step in self.state.steps.values():
            if not step.log_file:
                continue
            log = ROOT / step.log_file
            if not log.exists():
                continue
            _, found = parse(log.read_text(encoding="utf-8", errors="replace"))
            for c in found:
                if c.signature in causes:
                    causes[c.signature].errors += c.errors
                    causes[c.signature].derived += c.derived
                else:
                    causes[c.signature] = c
        return sorted(causes.values(), key=lambda c: (c.priority, -c.total))

    def cycle(self) -> bool:
        """Un passage complet. Retourne True si toutes les étapes aboutissent."""
        ctx = RunContext(env=self.env, stamp=self.state.stamp, logs_dir=self.logs,
                         with_emulator=not self.args.no_emulator,
                         with_autofix=not self.args.no_autofix,
                         with_instrumentation=not self.args.skip_instr,
                         with_provision=not self.args.no_provision)

        for runner in PIPELINE:
            status = self.run_step(runner, ctx)

            # Une étape qui modifie le code invalide les validations ultérieures.
            if status == Status.OK and runner.mutates_code:
                applied = self.state.step(runner.name).attempts
                if applied:
                    for later in PIPELINE[PIPELINE.index(runner) + 1:]:
                        self.state.step(later.name).status = Status.PENDING

            if status == Status.FAILED:
                # Si la compilation casse immédiatement après autofix, il faut
                # savoir laquelle des deux hypothèses est vraie : le code était
                # déjà cassé, ou autofix l'a cassé. On annule et on recompile :
                # la réponse est alors sans ambiguïté.
                if runner.name == "build" and ctx.autofix_baseline:
                    from engine.runners import revert_autofix
                    applied = self.state.step("autofix").attempts
                    if applied and revert_autofix(ctx.autofix_baseline):
                        print(f"{Y}↩{X} Compilation en échec après {applied} "
                              f"correction·s automatiques — annulation et "
                              f"nouvelle tentative")
                        ctx.autofix_baseline = None
                        self.state.step("autofix").detail = (
                            f"{applied} correction·s annulée·s (build cassé)")
                        self.state.step("autofix").status = Status.SKIPPED
                        self.state.step("build").status = Status.PENDING
                        if self.run_step(runner, ctx) == Status.OK:
                            print(f"  {R}→ autofix était la cause : "
                                  f"corrections écartées.{X}")
                            continue
                        print(f"  {D}→ le code était déjà en échec avant "
                              f"autofix.{X}")
                return False
        return True

    def run(self) -> int:
        self.discover()
        print()

        if self.env.strategy == "none" and not self.args.no_provision:
            # Le provisionnement peut installer ce qui manque : on lui laisse sa
            # chance avant de déclarer l'environnement inutilisable.
            from engine.runners import ProvisionRunner
            ctx = RunContext(env=self.env, stamp=self.state.stamp,
                             logs_dir=self.logs, with_provision=True)
            runner = ProvisionRunner()
            ok, reason = runner.applicable(ctx)
            if ok:
                print(f"{Y}Environnement incomplet — tentative de provisionnement{X}")
                if self.run_step(runner, ctx) == Status.OK:
                    self.env = ctx.env
                    self.state.strategy = self.env.strategy
                    print(f"  Stratégie après provisionnement : {self.env.strategy}\n")

        if self.env.strategy == "none":
            print(f"{R}Compilation impossible dans cet environnement.{X}")
            # Sans cette étape explicite, l'échec s'afficherait « étape None » :
            # aucun runner n'a tourné, donc aucun n'est en échec.
            blocked = self.state.step("environnement")
            blocked.status = Status.FAILED
            blocked.blocker = " · ".join(self.env.blockers) or "outillage absent"
            blocked.detail = blocked.blocker
            self._finish(success=False)
            return 1

        success = False
        for loop in range(1, self.args.max_loops + 1):
            self.state.iteration = loop
            if self.args.max_loops > 1:
                print(f"{B}── Passe {loop}/{self.args.max_loops} ──{X}")

            success = self.cycle()
            if success:
                break

            causes = self.analyze()
            self._print_causes(causes)

            # Une nouvelle passe n'a de sens que si quelque chose a changé
            # entre-temps. Sans correction applicable, relancer produirait
            # exactement le même échec.
            if loop < self.args.max_loops:
                fixable = self._apply_safe_fixes()
                if not fixable:
                    print(f"{D}Aucune correction automatique applicable — "
                          f"arrêt de la boucle.{X}")
                    break
                print(f"{G}{fixable} correction(s) appliquée(s) — "
                      f"nouvelle validation.{X}\n")
                for s in self.state.steps.values():
                    if s.status == Status.FAILED:
                        s.status = Status.PENDING

        self._finish(success)
        return 0 if success else 1

    def _apply_safe_fixes(self) -> int:
        proc = subprocess.run(
            [sys.executable, "software-factory/autofix/run.py", "--apply"],
            capture_output=True, text=True, cwd=ROOT)
        for line in proc.stdout.splitlines():
            if "correction(s) appliquée(s)" in line:
                try:
                    return int(line.strip().split()[0])
                except (ValueError, IndexError):
                    return 0
        return 0

    # ------------------------------------------------------------- sortie

    def _print_causes(self, causes: list[RootCause]) -> None:
        if not causes:
            return
        print(f"\n{B}Causes racines{X}")
        for i, c in enumerate(causes, 1):
            tag = f" {D}(dérivée){X}" if c.priority >= 90 else ""
            print(f"{i}. {R}{c.signature}{X} — {len(c.errors)} directe·s, "
                  f"{len(c.derived)} dérivée·s{tag}")
            print(f"   {D}{c.advice}{X}")
            for e in c.errors[:2]:
                loc = f"{e.file}:{e.line}" if e.file else ""
                print(f"     {D}{loc}{X} {e.message[:88]}")
        print(f"{D}Cycles économisés en corrigeant tout d'un coup : "
              f"{estimate_saved_cycles(causes)}{X}")

    def _finish(self, success: bool) -> None:
        self.state.finished = datetime.now().isoformat(timespec="seconds")
        self.state.save()

        subprocess.run(
            [sys.executable, "software-factory/orchestrator/publish.py",
             self.state.stamp, self.state.failed_step or ""],
            cwd=ROOT)

        print(f"\n{B}{'─' * 58}{X}")
        for s in self.state.steps.values():
            print(f"  {ICON[Status(s.status)]} {s.name:18} "
                  f"{D}{s.duration:6.1f}s{X}  {s.detail or s.blocker}")

        if success:
            print(f"\n{G}{B}═══ CYCLE RÉUSSI ═══{X}")
            print("Les bugs CORRIGÉ (INSPECTION) peuvent passer en CORRIGÉ (VALIDÉ).")
            self.state.clear()   # rien à reprendre
        else:
            print(f"\n{R}{B}═══ ÉCHEC — étape « {self.state.failed_step} » ═══{X}")
            recoverable = [s for s in self.state.steps.values() if s.done]
            if recoverable:
                print(f"Reprendre sans rejouer {len(recoverable)} étape·s validée·s :"
                      f"  {B}--resume{X}")
            else:
                blockers = getattr(self.env, "blockers", []) if self.env else []
                for b in blockers:
                    print(f"  {R}•{X} {b}")
        print(f"{D}Détail : software-factory/last-cycle/summary.md{X}")


def show_status() -> int:
    st = PipelineState.load()
    if not st:
        print("Aucun cycle enregistré.")
        return 0
    print(f"{B}Dernier cycle{X} — {st.stamp} · commit `{st.commit}` · "
          f"stratégie {st.strategy}")
    for s in st.steps.values():
        print(f"  {ICON[Status(s.status)]} {s.name:18} {s.detail or s.blocker}")
    if st.failed_step:
        print(f"\n{Y}Reprise possible :{X} python3 -m engine.engine --resume")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Execution Engine — point d'entrée de la Software Factory")
    ap.add_argument("--resume", action="store_true",
                    help="reprend le cycle interrompu sans rejouer les étapes validées")
    ap.add_argument("--status", action="store_true", help="état du dernier cycle")
    ap.add_argument("--max-loops", type=int, default=1,
                    help="nombre de boucles corriger→revalider")
    ap.add_argument("--no-emulator", action="store_true")
    ap.add_argument("--no-autofix", action="store_true")
    ap.add_argument("--skip-instr", action="store_true")
    ap.add_argument("--no-provision", action="store_true",
                    help="ne pas installer les composants Android manquants")
    args = ap.parse_args()

    if args.status:
        return show_status()
    return ExecutionEngine(args).run()


if __name__ == "__main__":
    sys.exit(main())
