#!/usr/bin/env python3
"""
Tests de l'analyseur — exécutables sans JDK.

    python3 software-factory/analyzers/test_gradle_log.py

Un analyseur qui classe mal les erreurs est pire qu'aucun analyseur : il
oriente les corrections dans la mauvaise direction. Ces cas figent le
comportement attendu.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from analyzers.gradle_log import estimate_saved_cycles, parse  # noqa: E402

CASES = [
    ("JDK incompatible",
     "error: Unsupported class file major version 65\n",
     "jdk", 1),
    ("dépendance non résolue",
     "error: Could not find androidx.appcompat:appcompat:1.7.0.\n",
     "dependency", 1),
    ("migration Room divergente",
     "error: Migration didn't properly handle: staff(StaffEntity)\n",
     "room-migration", 1),
    ("échec crypto dans un test",
     "BackupManagerTest > import FAILED\n    javax.crypto.AEADBadTagException: Tag mismatch\n",
     "crypto", 1),
    ("assertion en échec",
     "BackupFormatTest > detection FAILED\n    java.lang.AssertionError: expected:<A> but was:<B>\n",
     "assertion", 1),
    ("erreur de syntaxe Kotlin",
     "e: file:///w/app/src/main/A.kt:10:5 Expecting '}'\n",
     "syntax", 1),
    ("build réussi",
     "BUILD SUCCESSFUL in 42s\n",
     None, 0),
]


def test_classification() -> int:
    failures = 0
    for name, log, expected, min_errors in CASES:
        errors, causes = parse(log)
        sigs = [c.signature for c in causes]
        if expected is None:
            good = len(errors) == 0
        else:
            good = expected in sigs and len(errors) >= min_errors
        print(("  ✅ " if good else "  ❌ ") + f"{name:32} → {sigs}")
        failures += not good
    return failures


def test_derived_detection() -> int:
    """Une erreur dans un fichier doit rendre dérivées les références vers lui."""
    log = (
        "e: file:///w/app/src/main/BackupManager.kt:112:9 Type mismatch: "
        "inferred type is Result<Unit> but File was expected\n"
        "e: file:///w/app/src/main/MainRepository.kt:806:32 Unresolved reference: BackupManager\n"
        "e: file:///w/app/src/main/MainViewModel.kt:460:18 Unresolved reference: BackupManager\n"
    )
    _, causes = parse(log)
    unresolved = next((c for c in causes if c.signature == "unresolved"), None)
    ok = unresolved is not None and len(unresolved.derived) == 2 and not unresolved.errors
    print(("  ✅ " if ok else "  ❌ ") +
          f"2 références dérivées écartées → "
          f"{len(unresolved.derived) if unresolved else 0} dérivée(s)")

    first = causes[0].signature if causes else None
    ok2 = first == "type-mismatch"
    print(("  ✅ " if ok2 else "  ❌ ") + f"la vraie cause est prioritaire → {first}")
    return (not ok) + (not ok2)


def test_saved_cycles() -> int:
    log = (
        "error: Unsupported class file major version 65\n"
        "error: Could not find some:artifact:1.0\n"
        "e: file:///w/A.kt:1:1 Type mismatch\n"
    )
    _, causes = parse(log)
    saved = estimate_saved_cycles(causes)
    ok = saved == 2  # 3 causes indépendantes corrigées ensemble = 2 cycles évités
    print(("  ✅ " if ok else "  ❌ ") + f"cycles économisés = {saved} (attendu 2)")
    return not ok


if __name__ == "__main__":
    print("Classification des erreurs")
    f = test_classification()
    print("\nDétection des erreurs dérivées")
    f += test_derived_detection()
    print("\nEstimation des cycles économisés")
    f += test_saved_cycles()
    total = len(CASES) + 3
    print(f"\n{total - f}/{total} tests réussis")
    sys.exit(1 if f else 0)
