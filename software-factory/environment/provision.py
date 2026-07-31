#!/usr/bin/env python3
"""
Provisionnement automatique de l'environnement Android.

    python3 -m environment.provision --check     # diagnostic seul
    python3 -m environment.provision --apply     # installe ce qui manque

**Interventions humaines supprimées** (mesurées : 38 min bloquant le 1er cycle) :

| Avant                                              | Après             |
|----------------------------------------------------|-------------------|
| Android Studio → SDK Manager → cocher platform-35… | `sdkmanager` auto |
| Android Studio → Device Manager → Create Device    | `avdmanager` auto |
| accepter les licences une par une                  | `--licenses` auto |
| démarrer Docker Desktop à la main                  | tentative auto    |
| `adb kill-server` quand un appareil est « offline »| réparation auto   |

Ne fait rien sans `--apply` : télécharger plusieurs centaines de Mo doit rester
une décision explicite.
"""
from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "software-factory"))
from environment.detect import detect  # noqa: E402

G, R, Y, D, B, X = ("\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[1m", "\033[0m")

COMPILE_SDK = "35"          # app/build.gradle.kts
BUILD_TOOLS = "35.0.0"
# Image système de l'AVD : API 34 plutôt que 35 (disponibilité et stabilité
# meilleures), suffisante pour l'instrumentation qui cible minSdk 24.
AVD_IMAGE = "system-images;android-34;google_apis;x86_64"
AVD_NAME = "mobilecaisse-ci"


@dataclass
class Action:
    name: str
    needed: bool
    description: str
    command: list[str] | None = None
    manual_hint: str = ""
    done: bool = False
    error: str = ""


def _run(cmd: list[str], timeout: int = 1800, stdin: str | None = None) -> tuple[int, str]:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True,
                           timeout=timeout, input=stdin)
        return p.returncode, (p.stdout + p.stderr)[-4000:]
    except subprocess.TimeoutExpired:
        return 124, "délai dépassé"
    except Exception as e:
        return 1, str(e)


def _sdkmanager(sdk_path: str) -> str | None:
    exe = "sdkmanager.bat" if platform.system() == "Windows" else "sdkmanager"
    for sub in ("cmdline-tools/latest/bin", "cmdline-tools/bin", "tools/bin"):
        cand = Path(sdk_path) / sub / exe
        if cand.exists():
            return str(cand)
    return shutil.which("sdkmanager")


def _avdmanager(sdk_path: str) -> str | None:
    exe = "avdmanager.bat" if platform.system() == "Windows" else "avdmanager"
    for sub in ("cmdline-tools/latest/bin", "cmdline-tools/bin", "tools/bin"):
        cand = Path(sdk_path) / sub / exe
        if cand.exists():
            return str(cand)
    return shutil.which("avdmanager")


def plan(env) -> list[Action]:
    """Liste ce qui manque. Ne modifie rien."""
    actions: list[Action] = []
    sdk = env.sdk.path if env.sdk.found else ""
    sm = _sdkmanager(sdk) if sdk else None
    am = _avdmanager(sdk) if sdk else None

    # --- Docker : tenter de réveiller un démon arrêté
    if env.docker.found and not env.docker.usable:
        system = platform.system()
        cmd = None
        if system == "Darwin":
            cmd = ["open", "-a", "Docker"]
        elif system == "Linux":
            cmd = ["systemctl", "--user", "start", "docker-desktop"]
        actions.append(Action(
            "docker-daemon", True,
            "Docker installé mais démon arrêté", cmd,
            "Démarrer Docker Desktop manuellement"))

    # --- SDK absent : seul cas réellement non automatisable ici
    if not env.sdk.found:
        actions.append(Action(
            "sdk-missing", True,
            "SDK Android introuvable", None,
            "Installer Android Studio (il fournit le SDK), ou définir ANDROID_HOME"))
        return actions

    if not sm:
        actions.append(Action(
            "cmdline-tools", True,
            "sdkmanager introuvable", None,
            "Android Studio → SDK Manager → SDK Tools → "
            "cocher « Android SDK Command-line Tools »"))
    else:
        # Les paquets SDK ne sont interrogeables qu'avec sdkmanager. Sans lui,
        # on saute cette section sans abandonner les actions indépendantes
        # (émulateur, adb) qui suivent.
        code, out = _run([sm, "--list_installed"], timeout=120)
        if code != 0 and "license" in out.lower():
            actions.append(Action(
                "licences", True, "Licences SDK non acceptées",
                [sm, "--licenses"], "yes | sdkmanager --licenses"))

        installed = out
        if f"platforms;android-{COMPILE_SDK}" not in installed:
            actions.append(Action(
                "platform", True,
                f"Plateforme android-{COMPILE_SDK} absente (compileSdk)",
                [sm, f"platforms;android-{COMPILE_SDK}"]))
        if f"build-tools;{BUILD_TOOLS}" not in installed:
            actions.append(Action(
                "build-tools", True, f"Build-tools {BUILD_TOOLS} absents",
                [sm, f"build-tools;{BUILD_TOOLS}"]))
        if "platform-tools" not in installed:
            actions.append(Action(
                "platform-tools", True, "platform-tools (adb) absents",
                [sm, "platform-tools"]))

    # --- Émulateur : uniquement si aucun appareil physique n'est branché.
    # Créer un AVD alors qu'un téléphone est connecté téléchargerait plusieurs
    # centaines de Mo pour rien.
    physical = [d for d in env.devices if d.state == "device" and not d.is_emulator]
    if not physical and sm:
        if not env.emulator.found:
            actions.append(Action(
                "emulator-pkg", True, "Paquet émulateur absent", [sm, "emulator"]))
        if not env.avds:
            actions.append(Action(
                "avd-image", True, f"Image système absente ({AVD_IMAGE})",
                [sm, AVD_IMAGE]))
            if am:
                actions.append(Action(
                    "avd-create", True, f"Aucun AVD — création de « {AVD_NAME} »",
                    [am, "create", "avd", "-n", AVD_NAME, "-k", AVD_IMAGE,
                     "--device", "pixel_6", "--force"]))

    # --- adb bloqué : appareils vus mais inutilisables
    stuck = [d for d in env.devices if d.state in ("offline", "unauthorized")]
    if stuck and env.adb.usable:
        actions.append(Action(
            "adb-restart", True,
            f"{len(stuck)} appareil·s en état « {stuck[0].state} »",
            [env.adb.path, "kill-server"],
            "Débrancher/rebrancher le câble, puis autoriser le débogage USB"))

    return actions


def apply(actions: list[Action], env) -> int:
    sdk = env.sdk.path if env.sdk.found else ""
    applied = 0

    for a in actions:
        if not a.needed:
            continue
        print(f"\n{B}▸ {a.description}{X}")

        if a.command is None:
            print(f"  {Y}Action manuelle requise :{X} {a.manual_hint}")
            continue

        # Les licences exigent une confirmation répétée sur stdin.
        stdin = "y\n" * 40 if a.name == "licences" else None
        label = " ".join(Path(a.command[0]).name if i == 0 else c
                         for i, c in enumerate(a.command))
        print(f"  {D}$ {label}{X}")

        code, out = _run(a.command, stdin=stdin)
        if code == 0:
            a.done = True
            applied += 1
            print(f"  {G}✓{X} terminé")
            if a.name == "adb-restart":
                _run([env.adb.path, "start-server"], timeout=60)
        else:
            a.error = out.strip().splitlines()[-1] if out.strip() else f"code {code}"
            print(f"  {R}✗{X} {a.error[:160]}")
            if a.manual_hint:
                print(f"  {Y}↳ {a.manual_hint}{X}")

    return applied


def main() -> int:
    ap = argparse.ArgumentParser(description="Provisionnement de l'environnement Android")
    ap.add_argument("--apply", action="store_true",
                    help="installe réellement (téléchargements possibles)")
    ap.add_argument("--check", action="store_true", help="diagnostic seul")
    args = ap.parse_args()

    print(f"{B}▸ Provisionnement de l'environnement{X}")
    env = detect()
    actions = plan(env)

    if not actions:
        print(f"  {G}✓ Environnement complet — rien à installer.{X}")
        return 0

    auto = [a for a in actions if a.command]
    manual = [a for a in actions if not a.command]

    print(f"  {len(auto)} action·s automatisable·s, {len(manual)} manuelle·s\n")
    for a in actions:
        icon = f"{G}auto{X}" if a.command else f"{Y}manuel{X}"
        print(f"  [{icon}] {a.description}")
        if not a.command and a.manual_hint:
            print(f"          {D}{a.manual_hint}{X}")

    if args.check or not args.apply:
        print(f"\n  {D}Simulation. Utiliser --apply pour installer.{X}")
        if auto:
            print(f"  {D}Téléchargements possibles : plusieurs centaines de Mo.{X}")
        return 0 if not manual else 1

    applied = apply(actions, env)

    print(f"\n{B}{'─' * 58}{X}")
    print(f"  {applied}/{len(auto)} action·s appliquée·s")

    after = detect()
    if after.strategy != "none":
        print(f"  {G}✓ Stratégie disponible : {after.strategy}{X}")
        return 0
    print(f"  {R}✗ Environnement toujours incomplet{X}")
    for b in after.blockers:
        print(f"    • {b}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
