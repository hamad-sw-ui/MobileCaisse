#!/usr/bin/env python3
"""
Démarrage automatique d'un émulateur Android et attente de disponibilité.

    python3 software-factory/environment/emulator.py --start [--avd NOM]
    python3 software-factory/environment/emulator.py --ensure   # démarre si besoin
    python3 software-factory/environment/emulator.py --stop

**Intervention humaine supprimée** : « ouvrir Android Studio → Device Manager →
démarrer l'AVD → attendre le démarrage complet → relancer les tests ».

`--ensure` est le mode utilisé par le pipeline : il ne fait rien si un appareil
est déjà connecté, ce qui préserve le cas d'un téléphone physique branché.

⚠️ Un émulateur n'est PAS équivalent à un appareil réel pour
`SecurityMigrationTest` : l'AndroidKeyStore y est émulé logiciellement. Le
résultat est signalé comme tel dans le rapport (`.ai/DEV_ENVIRONMENT.md` §7).
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
from detect import detect  # noqa: E402

G, R, Y, D, B, X = ("\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[1m", "\033[0m")

BOOT_TIMEOUT = 300   # 5 min : un démarrage à froid est lent sur machine chargée
POLL = 5


def _adb(adb: str, *args: str, timeout: int = 20) -> tuple[int, str]:
    try:
        p = subprocess.run([adb, *args], capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout + p.stderr).strip()
    except Exception as e:
        return 1, str(e)


def connected_devices(adb: str) -> list[str]:
    _, out = _adb(adb, "devices")
    return [ln.split()[0] for ln in out.splitlines()[1:]
            if ln.strip() and ln.split()[-1] == "device"]


def wait_until_ready(adb: str, serial: str | None = None, timeout: int = BOOT_TIMEOUT) -> str | None:
    """
    Attend qu'un appareil soit **réellement** utilisable.

    `adb devices` affiche « device » bien avant la fin du démarrage : lancer les
    tests à ce moment échoue avec des erreurs trompeuses. On attend donc
    `sys.boot_completed=1`, puis la disparition de l'animation de démarrage.
    """
    print(f"  {D}Attente du démarrage (jusqu'à {timeout}s)…{X}")
    start = time.time()
    target = serial

    while time.time() - start < timeout:
        if target is None:
            devices = connected_devices(adb)
            if devices:
                target = devices[0]
                print(f"  {D}Appareil détecté : {target}{X}")

        if target:
            _, boot = _adb(adb, "-s", target, "shell", "getprop", "sys.boot_completed")
            if boot.strip() == "1":
                # L'animation de démarrage peut encore masquer l'écran.
                for _ in range(12):
                    _, anim = _adb(adb, "-s", target, "shell", "getprop",
                                   "init.svc.bootanim")
                    if anim.strip() == "stopped":
                        break
                    time.sleep(POLL)
                elapsed = int(time.time() - start)
                print(f"  {G}✓{X} Appareil prêt : {target} ({elapsed}s)")
                return target

        time.sleep(POLL)

    print(f"  {R}✗{X} Délai dépassé après {timeout}s")
    return None


def start(avd: str | None, headless: bool = True) -> int:
    env = detect()
    if not env.emulator.found:
        print(f"{R}✗ Émulateur introuvable.{X}")
        print(f"  {D}Installer via Android Studio → SDK Manager → SDK Tools → "
              f"Android Emulator{X}")
        return 1
    if not env.avds:
        print(f"{R}✗ Aucun AVD configuré.{X}")
        print(f"  {D}Créer un AVD : Android Studio → Device Manager → Create Device{X}")
        print(f"  {D}Ou en ligne de commande :{X}")
        print(f"    sdkmanager \"system-images;android-34;google_apis;x86_64\"")
        print(f"    avdmanager create avd -n mobilecaisse -k "
              f"\"system-images;android-34;google_apis;x86_64\"")
        return 1

    name = avd or env.avds[0]
    if name not in env.avds:
        print(f"{R}✗ AVD « {name} » inconnu.{X} Disponibles : {', '.join(env.avds)}")
        return 1

    print(f"{B}▸ Démarrage de l'émulateur « {name} »{X}")
    cmd = [env.emulator.path, "-avd", name, "-no-snapshot-load"]
    if headless:
        # Sans fenêtre ni audio : indispensable en CI, plus rapide en local.
        cmd += ["-no-window", "-no-audio", "-gpu", "swiftshader_indirect"]

    log = ROOT / "software-factory" / "cache" / "emulator.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w", encoding="utf-8") as fh:
        subprocess.Popen(cmd, stdout=fh, stderr=fh, start_new_session=True)
    print(f"  {D}Journal : {log.relative_to(ROOT)}{X}")

    ready = wait_until_ready(env.adb.path)
    if not ready:
        print(f"  {D}Consulter {log.relative_to(ROOT)} pour la cause.{X}")
        return 1

    # Réduit le bruit visuel et accélère les tests d'interface.
    for prop in ("window_animation_scale", "transition_animation_scale",
                 "animator_duration_scale"):
        _adb(env.adb.path, "-s", ready, "shell", "settings", "put", "global", prop, "0")
    return 0


def ensure(avd: str | None) -> int:
    """Ne démarre un émulateur que si aucun appareil n'est déjà disponible."""
    env = detect()
    if not env.adb.usable:
        print(f"{R}✗ adb indisponible : impossible de vérifier les appareils.{X}")
        return 1

    ready = [d for d in env.devices if d.state == "device"]
    if ready:
        d = ready[0]
        kind = "émulateur" if d.is_emulator else "appareil physique"
        print(f"{G}✓{X} {kind} déjà disponible : {d.serial} "
              f"{'(API ' + d.api + ')' if d.api else ''}")
        if not d.is_emulator:
            print(f"  {D}Appareil réel : résultats fiables pour SecurityMigrationTest.{X}")
        return 0

    print(f"{Y}Aucun appareil connecté — démarrage d'un émulateur.{X}")
    print(f"  {D}⚠️ AndroidKeyStore émulé logiciellement : les tests de sécurité")
    print(f"     ne sont pas pleinement représentatifs (DEV_ENVIRONMENT.md §7).{X}")
    return start(avd)


def stop() -> int:
    env = detect()
    if not env.adb.usable:
        return 1
    stopped = 0
    for d in env.devices:
        if d.is_emulator:
            _adb(env.adb.path, "-s", d.serial, "emu", "kill")
            print(f"  {G}✓{X} {d.serial} arrêté")
            stopped += 1
    if not stopped:
        print(f"  {D}Aucun émulateur en cours.{X}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Gestion de l'émulateur Android")
    ap.add_argument("--start", action="store_true", help="démarre un émulateur")
    ap.add_argument("--ensure", action="store_true",
                    help="démarre seulement si aucun appareil n'est disponible")
    ap.add_argument("--stop", action="store_true", help="arrête les émulateurs")
    ap.add_argument("--avd", help="nom de l'AVD")
    ap.add_argument("--window", action="store_true", help="affiche la fenêtre")
    args = ap.parse_args()

    if args.stop:
        return stop()
    if args.ensure:
        return ensure(args.avd)
    if args.start:
        return start(args.avd, headless=not args.window)
    ap.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
