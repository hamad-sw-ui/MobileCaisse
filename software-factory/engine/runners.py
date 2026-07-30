#!/usr/bin/env python3
"""
Runners — une étape = une classe. Toute la logique d'exécution vit ici.

Chaque runner déclare :
  - s'il est **applicable** dans l'environnement courant (sinon SKIPPED, avec motif) ;
  - s'il est **réessayable** (échec transitoire vs défaut de code) ;
  - s'il **modifie le code** (auquel cas les étapes suivantes sont invalidées).

Auparavant cette logique existait en double : en bash dans `full-cycle.sh` et en
Python dans `run.py`, avec des comportements divergents.
"""
from __future__ import annotations

import os
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))

from engine.state import Status, StepState  # noqa: E402


@dataclass
class RunContext:
    env: object                  # environment.detect.Environment
    stamp: str
    logs_dir: Path
    with_emulator: bool = True
    with_autofix: bool = True
    with_instrumentation: bool = True

    def gradle_env(self) -> dict[str, str]:
        """Variables d'environnement pour un appel Gradle local."""
        e = dict(os.environ)
        jdk = getattr(self.env, "jdk", None)
        sdk = getattr(self.env, "sdk", None)
        if jdk and jdk.usable and jdk.path:
            e["JAVA_HOME"] = str(Path(jdk.path).parent.parent)
        if sdk and sdk.found and sdk.path:
            e["ANDROID_HOME"] = sdk.path
            e["ANDROID_SDK_ROOT"] = sdk.path
        return e


class Runner:
    """Contrat commun à toutes les étapes."""

    name = "runner"
    retryable = False        # relancer tel quel peut-il aboutir ?
    mutates_code = False     # invalide les étapes déjà validées ?

    def applicable(self, ctx: RunContext) -> tuple[bool, str]:
        return True, ""

    def execute(self, ctx: RunContext, step: StepState) -> int:
        raise NotImplementedError

    # ------------------------------------------------------------ utilitaire

    def _run(self, cmd: list[str], ctx: RunContext, step: StepState,
             use_shell_env: bool = False) -> int:
        log_path = ctx.logs_dir / f"{self.name}_{ctx.stamp}.log"
        step.log_file = str(log_path.relative_to(ROOT))
        env = ctx.gradle_env() if use_shell_env else None
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True,
                                  cwd=ROOT, env=env)
        except FileNotFoundError as e:
            step.detail = f"commande introuvable : {e}"
            return 127
        output = proc.stdout + proc.stderr
        log_path.parent.mkdir(parents=True, exist_ok=True)
        log_path.write_text(output, encoding="utf-8")
        tail = [ln for ln in proc.stdout.strip().splitlines() if ln.strip()]
        step.detail = tail[-1][:120] if tail else f"code {proc.returncode}"
        return proc.returncode


# ------------------------------------------------------------------ étapes

class PreflightRunner(Runner):
    name = "preflight"
    retryable = False

    def execute(self, ctx: RunContext, step: StepState) -> int:
        return self._run(
            [sys.executable, "software-factory/preflight/run.py", "--report"],
            ctx, step)


class AutofixRunner(Runner):
    name = "autofix"
    mutates_code = True      # invalide compilation et tests déjà passés

    def applicable(self, ctx: RunContext) -> tuple[bool, str]:
        if not ctx.with_autofix:
            return False, "désactivé (--no-autofix)"
        return True, ""

    def execute(self, ctx: RunContext, step: StepState) -> int:
        code = self._run(
            [sys.executable, "software-factory/autofix/run.py", "--apply"],
            ctx, step)
        log = ROOT / step.log_file
        if log.exists():
            for line in log.read_text(encoding="utf-8").splitlines():
                if "correction(s) appliquée(s)" in line:
                    try:
                        step.detail = line.strip()
                        # « N correction(s) appliquée(s). »
                        n = int(line.strip().split()[0])
                        step.attempts = n
                    except (ValueError, IndexError):
                        pass
        return code


class BuildRunner(Runner):
    name = "build"
    retryable = True         # échec possible sur verrou Gradle ou mémoire

    def applicable(self, ctx: RunContext) -> tuple[bool, str]:
        if ctx.env.strategy == "none":
            return False, "aucune stratégie de compilation (ni Docker ni JDK 21)"
        return True, ""

    def execute(self, ctx: RunContext, step: StepState) -> int:
        if ctx.env.strategy == "docker":
            return self._run(["bash", "docker/scripts/build.sh", "debug"], ctx, step)
        return self._run(["sh", "./gradlew", "assembleDebug", "--warning-mode", "all"],
                         ctx, step, use_shell_env=True)


class UnitTestRunner(Runner):
    name = "tests"
    retryable = False        # un test qui échoue échouera encore

    def applicable(self, ctx: RunContext) -> tuple[bool, str]:
        if ctx.env.strategy == "none":
            return False, "aucune stratégie de compilation"
        return True, ""

    def execute(self, ctx: RunContext, step: StepState) -> int:
        if ctx.env.strategy == "docker":
            return self._run(["bash", "docker/scripts/test.sh"], ctx, step)
        return self._run(["sh", "./gradlew", "testDebugUnitTest"], ctx, step,
                         use_shell_env=True)


class InstrumentationRunner(Runner):
    name = "instrumentation"
    retryable = True         # un appareil peut se déconnecter

    def applicable(self, ctx: RunContext) -> tuple[bool, str]:
        if not ctx.with_instrumentation:
            return False, "désactivé (--skip-instr)"
        if not getattr(ctx.env, "adb", None) or not ctx.env.adb.usable:
            return False, "adb indisponible"
        ready = [d for d in ctx.env.devices if d.state == "device"]
        if not ready and not ctx.with_emulator:
            return False, "aucun appareil, démarrage d'émulateur désactivé"
        if not ready and not ctx.env.avds:
            return False, "aucun appareil connecté, aucun AVD configuré"
        return True, ""

    def execute(self, ctx: RunContext, step: StepState) -> int:
        ready = [d for d in ctx.env.devices if d.state == "device"]
        if not ready:
            code = self._run(
                [sys.executable, "software-factory/environment/emulator.py", "--ensure"],
                ctx, step)
            if code != 0:
                step.detail = "démarrage de l'émulateur échoué"
                return code
        return self._run(["sh", "./gradlew", "connectedDebugAndroidTest"], ctx, step,
                         use_shell_env=True)


PIPELINE: list[Runner] = [
    PreflightRunner(),
    AutofixRunner(),
    BuildRunner(),
    UnitTestRunner(),
    InstrumentationRunner(),
]
