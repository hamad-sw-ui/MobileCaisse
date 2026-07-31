#!/usr/bin/env python3
"""
Tests du provisionnement — exécutables sans SDK ni Docker.

    python3 -m environment.test_provision

Le provisionnement lance des installations et modifie l'environnement du
développeur : un plan erroné y coûte cher (téléchargements inutiles, AVD créé
alors qu'un téléphone est branché). Ces cas figent le comportement attendu.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from environment.detect import Device, Environment, Tool  # noqa: E402
from environment import provision  # noqa: E402
from environment.provision import plan  # noqa: E402

G, R, X = "\033[32m", "\033[31m", "\033[0m"

# `plan()` interroge le vrai sdkmanager, absent en test. On le neutralise :
# ces cas valident la LOGIQUE DE DÉCISION, pas l'appel au SDK réel.
_INSTALLED = (f"platforms;android-{provision.COMPILE_SDK}\n"
              f"build-tools;{provision.BUILD_TOOLS}\nplatform-tools\n")
provision._sdkmanager = lambda p: "/fake/sdkmanager"
provision._avdmanager = lambda p: "/fake/avdmanager"
provision._run = lambda cmd, timeout=1800, stdin=None: (0, _INSTALLED)


def base_env(**kw) -> Environment:
    env = Environment(os="Linux", arch="x86_64")
    env.docker = Tool("docker", True, "/usr/bin/docker", "27", True, "opérationnel")
    env.jdk = Tool("jdk", True, "/jbr/bin/java", "21", True, "JDK 21")
    env.sdk = Tool("android_sdk", True, "/sdk", "35", True, "platforms : 35")
    env.adb = Tool("adb", True, "/sdk/platform-tools/adb", "", True)
    env.emulator = Tool("emulator", True, "/sdk/emulator/emulator", "", True)
    env.avds = ["pixel"]
    env.devices = []
    for k, v in kw.items():
        setattr(env, k, v)
    return env


def names(env) -> list[str]:
    return [a.name for a in plan(env)]


CASES = []


def case(fn):
    CASES.append(fn)
    return fn


@case
def sdk_absent_est_manuel() -> tuple[bool, str]:
    env = base_env(sdk=Tool("android_sdk", False, note="introuvable"))
    actions = plan(env)
    ok = len(actions) == 1 and actions[0].name == "sdk-missing" and actions[0].command is None
    return ok, "SDK absent → une seule action, manuelle"


@case
def docker_arrete_est_automatisable() -> tuple[bool, str]:
    env = base_env(docker=Tool("docker", True, "/usr/bin/docker", "27", False, "démon arrêté"))
    actions = [a for a in plan(env) if a.name == "docker-daemon"]
    ok = len(actions) == 1
    return ok, "démon Docker arrêté → action planifiée"


@case
def appareil_physique_evite_la_creation_d_avd() -> tuple[bool, str]:
    # Créer un AVD alors qu'un téléphone est branché serait un téléchargement
    # de plusieurs centaines de Mo pour rien.
    env = base_env(
        emulator=Tool("emulator", False), avds=[],
        devices=[Device("R5CT30", "device", "SM-A155F", "34", is_emulator=False)])
    n = names(env)
    ok = "avd-create" not in n and "avd-image" not in n
    return ok, "appareil physique branché → aucun AVD créé"


@case
def sans_appareil_ni_avd_on_provisionne() -> tuple[bool, str]:
    env = base_env(emulator=Tool("emulator", False), avds=[], devices=[])
    n = names(env)
    ok = "emulator-pkg" in n and "avd-image" in n
    return ok, "ni appareil ni AVD → émulateur provisionné"


@case
def appareil_offline_declenche_adb_restart() -> tuple[bool, str]:
    env = base_env(devices=[Device("emulator-5554", "offline", "", "")])
    ok = "adb-restart" in names(env)
    return ok, "appareil offline → redémarrage d'adb planifié"


@case
def environnement_complet_ne_planifie_rien() -> tuple[bool, str]:
    env = base_env(devices=[Device("R5CT30", "device", "SM-A155F", "34")])
    ok = plan(env) == []
    return ok, "environnement complet → aucune action"


def main() -> int:
    failures = 0
    for fn in CASES:
        try:
            ok, label = fn()
        except Exception as e:
            ok, label = False, f"{fn.__name__} → exception : {e}"
        print(f"  {G}✅{X} {label}" if ok else f"  {R}❌{X} {label}")
        failures += not ok
    total = len(CASES)
    print(f"\n{total - failures}/{total} tests réussis")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
