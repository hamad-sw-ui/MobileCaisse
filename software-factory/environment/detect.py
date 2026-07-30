#!/usr/bin/env python3
"""
Détection automatique de l'environnement de build.

    python3 software-factory/environment/detect.py           # rapport lisible
    python3 software-factory/environment/detect.py --json    # sortie machine
    python3 software-factory/environment/detect.py --export  # variables shell

**Intervention humaine supprimée** : « installer Docker », « configurer
JAVA_HOME », « où est mon SDK ? ». Le pipeline s'arrêtait faute de Docker alors
qu'Android Studio embarque un JDK 21 (JetBrains Runtime) et le SDK Android.
Ce module trouve ce qui est disponible et choisit la meilleure stratégie.

Ordre de préférence :
  1. Docker      — reproductible, isolé (recommandé)
  2. JDK local   — celui d'Android Studio si présent, sinon système
  3. Aucun       — diagnostic actionnable, pas un simple échec
"""
from __future__ import annotations

import json
import os
import platform
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_JDK = 21  # imposé par gradle/gradle-daemon-jvm.properties


def _run(cmd: list[str], timeout: int = 15) -> tuple[int, str]:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout + p.stderr).strip()
    except Exception:
        return 1, ""


@dataclass
class Tool:
    name: str
    found: bool = False
    path: str = ""
    version: str = ""
    usable: bool = False
    note: str = ""


@dataclass
class Device:
    serial: str
    state: str
    model: str = ""
    api: str = ""
    is_emulator: bool = False


@dataclass
class Environment:
    os: str = ""
    arch: str = ""
    docker: Tool = field(default_factory=lambda: Tool("docker"))
    jdk: Tool = field(default_factory=lambda: Tool("jdk"))
    android_studio: Tool = field(default_factory=lambda: Tool("android_studio"))
    sdk: Tool = field(default_factory=lambda: Tool("android_sdk"))
    adb: Tool = field(default_factory=lambda: Tool("adb"))
    emulator: Tool = field(default_factory=lambda: Tool("emulator"))
    gradlew: Tool = field(default_factory=lambda: Tool("gradlew"))
    devices: list[Device] = field(default_factory=list)
    avds: list[str] = field(default_factory=list)
    strategy: str = "none"
    blockers: list[str] = field(default_factory=list)


# --------------------------------------------------------------- emplacements

def _android_studio_paths() -> list[Path]:
    home = Path.home()
    system = platform.system()
    if system == "Darwin":
        return [Path("/Applications/Android Studio.app/Contents"),
                home / "Applications/Android Studio.app/Contents"]
    if system == "Windows":
        return [Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Android/Android Studio",
                Path(os.environ.get("LOCALAPPDATA", "")) / "Programs/Android Studio"]
    return [Path("/opt/android-studio"), Path("/usr/local/android-studio"),
            home / "android-studio", home / ".local/share/JetBrains/Toolbox/apps/AndroidStudio"]


def _sdk_paths() -> list[Path]:
    home = Path.home()
    env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    out = [Path(env)] if env else []
    system = platform.system()
    if system == "Darwin":
        out.append(home / "Library/Android/sdk")
    elif system == "Windows":
        out.append(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk")
    else:
        out += [home / "Android/Sdk", Path("/usr/lib/android-sdk"), Path("/opt/android-sdk")]
    return out


# ------------------------------------------------------------------ détecteurs

def detect_docker() -> Tool:
    t = Tool("docker")
    exe = shutil.which("docker")
    if not exe:
        t.note = "non installé"
        return t
    t.found, t.path = True, exe
    code, out = _run(["docker", "--version"])
    t.version = out.splitlines()[0] if out else ""
    code, _ = _run(["docker", "info"], timeout=20)
    t.usable = code == 0
    t.note = "opérationnel" if t.usable else "installé mais démon arrêté"
    return t


def detect_jdk(studio: Tool) -> Tool:
    """
    Cherche un JDK 21. Priorité au JetBrains Runtime d'Android Studio : c'est
    le JDK avec lequel l'IDE compile déjà le projet, donc le plus cohérent.
    """
    t = Tool("jdk")
    candidates: list[Path] = []

    if studio.found and studio.path:
        base = Path(studio.path)
        candidates += [base / "jbr/Contents/Home/bin/java",  # macOS
                       base / "jbr/bin/java"]                # Linux / Windows

    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]) / "bin/java")

    system = shutil.which("java")
    if system:
        candidates.append(Path(system))

    for p in ("/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"):
        d = Path(p)
        if d.exists():
            for jvm in sorted(d.iterdir(), reverse=True):
                for sub in ("bin/java", "Contents/Home/bin/java"):
                    candidates.append(jvm / sub)

    fallback: Tool | None = None
    for cand in candidates:
        if not cand.exists():
            continue
        code, out = _run([str(cand), "-version"])
        if code != 0:
            continue
        m = re.search(r'version "?(\d+)', out)
        major = int(m.group(1)) if m else 0
        tool = Tool("jdk", True, str(cand), out.splitlines()[0] if out else "",
                    major == REQUIRED_JDK)
        if tool.usable:
            tool.note = f"JDK {major} — conforme (toolchainVersion={REQUIRED_JDK})"
            if studio.found and "jbr" in str(cand):
                tool.note += ", fourni par Android Studio"
            return tool
        if fallback is None:
            fallback = tool
            fallback.note = f"JDK {major} trouvé, mais le projet exige le {REQUIRED_JDK}"

    if fallback:
        return fallback
    t.note = f"aucun JDK trouvé (le projet exige le {REQUIRED_JDK})"
    return t


def detect_android_studio() -> Tool:
    t = Tool("android_studio")
    for p in _android_studio_paths():
        if p.exists():
            t.found, t.path, t.usable = True, str(p), True
            t.note = "installé"
            return t
    t.note = "non détecté aux emplacements usuels"
    return t


def detect_sdk() -> Tool:
    t = Tool("android_sdk")
    for p in _sdk_paths():
        if p.exists() and (p / "platform-tools").exists():
            t.found, t.path = True, str(p)
            plats = sorted((p / "platforms").glob("android-*")) if (p / "platforms").exists() else []
            versions = [x.name.replace("android-", "") for x in plats]
            t.version = ", ".join(versions)
            t.usable = "35" in versions
            t.note = (f"platforms : {t.version or 'aucune'}"
                      + ("" if t.usable else " — android-35 requis (compileSdk)"))
            return t
    t.note = "introuvable (définir ANDROID_HOME)"
    return t


def detect_adb(sdk: Tool) -> Tool:
    t = Tool("adb")
    exe = shutil.which("adb")
    if not exe and sdk.found:
        cand = Path(sdk.path) / "platform-tools" / ("adb.exe" if platform.system() == "Windows" else "adb")
        if cand.exists():
            exe = str(cand)
    if not exe:
        t.note = "non trouvé"
        return t
    t.found, t.path, t.usable = True, exe, True
    _, out = _run([exe, "version"])
    t.version = out.splitlines()[0] if out else ""
    return t


def detect_emulator(sdk: Tool) -> tuple[Tool, list[str]]:
    t = Tool("emulator")
    exe = shutil.which("emulator")
    if not exe and sdk.found:
        cand = Path(sdk.path) / "emulator" / ("emulator.exe" if platform.system() == "Windows" else "emulator")
        if cand.exists():
            exe = str(cand)
    if not exe:
        t.note = "non trouvé"
        return t, []
    t.found, t.path = True, exe
    code, out = _run([exe, "-list-avds"], timeout=20)
    avds = [ln.strip() for ln in out.splitlines()
            if ln.strip() and not ln.startswith(("INFO", "WARNING", "ERROR"))]
    t.usable = bool(avds)
    t.note = f"{len(avds)} AVD disponible·s" if avds else "aucun AVD configuré"
    return t, avds


def detect_devices(adb: Tool) -> list[Device]:
    if not adb.usable:
        return []
    _, out = _run([adb.path, "devices", "-l"], timeout=20)
    devices = []
    for ln in out.splitlines()[1:]:
        parts = ln.split()
        if len(parts) < 2 or parts[1] not in ("device", "offline", "unauthorized"):
            continue
        serial, state = parts[0], parts[1]
        model = next((p.split(":", 1)[1] for p in parts if p.startswith("model:")), "")
        api = ""
        if state == "device":
            _, api = _run([adb.path, "-s", serial, "shell", "getprop", "ro.build.version.sdk"])
            api = api.strip()
        devices.append(Device(serial, state, model, api, serial.startswith("emulator-")))
    return devices


# ------------------------------------------------------------------ synthèse

def detect() -> Environment:
    env = Environment(os=platform.system(), arch=platform.machine())
    env.docker = detect_docker()
    env.android_studio = detect_android_studio()
    env.jdk = detect_jdk(env.android_studio)
    env.sdk = detect_sdk()
    env.adb = detect_adb(env.sdk)
    env.emulator, env.avds = detect_emulator(env.sdk)
    env.devices = detect_devices(env.adb)

    gw = ROOT / "gradlew"
    env.gradlew = Tool("gradlew", gw.exists(), str(gw), "",
                       gw.exists(), "présent" if gw.exists() else "absent")

    # Choix de la stratégie : Docker d'abord (reproductible), sinon JDK local.
    if env.docker.usable:
        env.strategy = "docker"
    elif env.jdk.usable and env.sdk.usable and env.gradlew.found:
        env.strategy = "local"
    else:
        env.strategy = "none"
        if not env.docker.found:
            env.blockers.append("Docker absent — https://docs.docker.com/get-docker/")
        elif not env.docker.usable:
            env.blockers.append("Démon Docker arrêté — démarrer Docker Desktop")
        if not env.jdk.usable:
            env.blockers.append(f"JDK {REQUIRED_JDK} requis — {env.jdk.note}")
        if not env.sdk.usable:
            env.blockers.append(f"SDK Android — {env.sdk.note}")
    return env


def print_report(env: Environment) -> None:
    G, R, Y, D, B, X = ("\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[1m", "\033[0m")

    def mark(t: Tool) -> str:
        return f"{G}✓{X}" if t.usable else (f"{Y}~{X}" if t.found else f"{R}✗{X}")

    print(f"{B}▸ Environnement{X} — {env.os} {env.arch}\n")
    for t in (env.docker, env.jdk, env.android_studio, env.sdk, env.adb,
              env.emulator, env.gradlew):
        line = f"  {mark(t)} {t.name:16} {t.note}"
        if t.version:
            line += f"  {D}{t.version[:52]}{X}"
        print(line)

    if env.avds:
        print(f"\n  {D}AVD : {', '.join(env.avds)}{X}")
    if env.devices:
        print(f"\n{B}  Appareils{X}")
        for d in env.devices:
            icon = f"{G}✓{X}" if d.state == "device" else f"{Y}~{X}"
            kind = "émulateur" if d.is_emulator else "physique"
            print(f"  {icon} {d.serial:22} {kind:10} {d.model} "
                  f"{'API ' + d.api if d.api else d.state}")

    print(f"\n{B}  Stratégie : ", end="")
    if env.strategy == "docker":
        print(f"{G}Docker{X} — build reproductible")
    elif env.strategy == "local":
        print(f"{Y}JDK local{X} — Docker indisponible, repli sur Gradle")
    else:
        print(f"{R}aucune{X} — compilation impossible")
    print(X, end="")

    if env.blockers:
        print(f"\n{R}  Points bloquants{X}")
        for b in env.blockers:
            print(f"    • {b}")

    ready = [d for d in env.devices if d.state == "device"]
    if not ready:
        if env.avds:
            print(f"\n  {Y}Aucun appareil connecté — un émulateur peut être démarré :{X}")
            print(f"    python3 software-factory/environment/emulator.py --start")
        else:
            print(f"\n  {Y}Tests d'instrumentation indisponibles{X} "
                  f"{D}(ni appareil, ni AVD){X}")


def main() -> int:
    env = detect()
    if "--json" in sys.argv:
        print(json.dumps(asdict(env), indent=2, ensure_ascii=False))
    elif "--export" in sys.argv:
        print(f"SF_STRATEGY={env.strategy}")
        print(f"SF_JDK={env.jdk.path if env.jdk.usable else ''}")
        print(f"SF_SDK={env.sdk.path if env.sdk.found else ''}")
        print(f"SF_ADB={env.adb.path if env.adb.usable else ''}")
        print(f"SF_EMULATOR={env.emulator.path if env.emulator.found else ''}")
        print(f"SF_DEVICES={len([d for d in env.devices if d.state == 'device'])}")
        print(f"SF_AVDS={','.join(env.avds)}")
    else:
        print_report(env)
    return 0 if env.strategy != "none" else 1


if __name__ == "__main__":
    sys.exit(main())
