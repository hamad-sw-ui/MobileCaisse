#!/usr/bin/env python3
"""
Analyseur de journaux Gradle / Kotlin / KSP.

Transforme une sortie de build brute en causes racines exploitables (§19.2/19.3).

Conçu pour être testable **sans Gradle** : `parse()` prend une chaîne en entrée.
C'est ce qui permet de le valider dans un sandbox sans JDK, à partir
d'échantillons de journaux réels.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field


@dataclass
class BuildError:
    """Une erreur atomique, telle qu'émise par le compilateur."""
    file: str
    line: int
    column: int
    message: str
    kind: str          # "kotlin" | "ksp" | "gradle" | "test" | "resource"
    raw: str = ""


@dataclass
class RootCause:
    """Un groupe d'erreurs partageant la même origine."""
    signature: str
    category: str
    errors: list[BuildError] = field(default_factory=list)
    derived: list[BuildError] = field(default_factory=list)
    priority: int = 99
    advice: str = ""

    @property
    def total(self) -> int:
        return len(self.errors) + len(self.derived)


# Motifs d'erreur Kotlin, du plus spécifique au plus général.
# `advice` doit orienter vers la CAUSE, pas décrire le symptôme.
# ⚠️ `_classify` compare sur le message EN MINUSCULES : tout motif doit être
# écrit en minuscules, sinon il ne matchera jamais. Défaut réel rencontré le
# 2026-07-30 (AEADBadTagException classé « unknown »), figé par un test.
KOTLIN_PATTERNS: list[tuple[str, str, int, str]] = [
    (r"unresolved reference:?\s*(\w+)", "unresolved", 8,
     "Symbole introuvable : import manquant, faute de frappe, ou déclaré dans un "
     "fichier lui-même en erreur. Traiter d'abord les autres causes."),
    (r"(?:type mismatch|inferred type is)", "type-mismatch", 2,
     "Incompatibilité de types : vérifier la signature réelle et le type de retour."),
    (r"none of the following (?:functions|candidates)", "overload", 2,
     "Aucune surcharge ne correspond : vérifier le nombre et le type des arguments."),
    (r"(?:val|var) cannot be reassigned", "immutability", 3,
     "Réassignation d'un `val` : le déclarer `var` ou revoir la logique."),
    (r"smart cast to .* is impossible", "smart-cast", 3,
     "Smart-cast impossible sur une propriété mutable : passer par une copie locale."),
    (r"(?:expecting|unexpected) (?:'|\")?[}{)(]", "syntax", 1,
     "Erreur de syntaxe : délimiteur manquant. `preflight` aurait dû la détecter."),
    (r"conflicting (?:declarations|overloads)", "conflict", 2,
     "Déclarations en conflit : doublon de nom dans la même portée."),
    (r"class .* must be declared abstract or implement", "abstract", 2,
     "Membre abstrait non implémenté."),
    (r"cannot access .* it is (?:private|internal|protected)", "visibility", 2,
     "Visibilité insuffisante."),
    (r"@\w+ .* not applicable", "annotation", 2,
     "Annotation mal placée."),
]

KSP_PATTERNS: list[tuple[str, str, int, str]] = [
    (r"room.*(?:cannot find|cannot figure out|not found)", "room-schema", 1,
     "Room ne résout pas le schéma : entité, DAO ou TypeConverter manquant."),
    (r"migration didn't properly handle", "room-migration", 1,
     "Migration divergente de l'entité — BUG-001. Comparer au schéma généré."),
    (r"ksp.*(?:error|failed)", "ksp", 2,
     "Échec du traitement d'annotations : souvent la conséquence d'une entité "
     "qui ne compile pas. Corriger les erreurs Kotlin d'abord."),
    (r"serializer has not been found", "serialization", 2,
     "Type non sérialisable : ajouter @Serializable ou un sérialiseur dédié."),
]

GRADLE_PATTERNS: list[tuple[str, str, int, str]] = [
    (r"could not (?:find|resolve) .*(?:artifact|dependency|:)", "dependency", 1,
     "Dépendance non résolue : vérifier le version catalog et les dépôts."),
    (r"could not find method (\w+)", "dsl", 1,
     "Méthode DSL inconnue : plugin non appliqué ou syntaxe d'une autre version d'AGP."),
    (r"plugin .* not found", "plugin", 1,
     "Plugin absent : vérifier `pluginManagement` et le catalog."),
    (r"sdk location not found|android_home", "sdk", 1,
     "SDK Android introuvable : problème d'environnement, pas de code."),
    (r"failed to (?:transform|process) resource|aapt2", "resource", 1,
     "Échec du packaging des ressources : référence absente ou XML invalide."),
    (r"duplicate class", "duplicate-class", 1,
     "Classe en double : conflit de dépendances."),
    (r"unsupported class file major version", "jdk", 1,
     "JDK incompatible : le projet exige le JDK 21 (toolchainVersion=21)."),
    (r"out of memory|outofmemoryerror|gc overhead", "oom", 1,
     "Mémoire insuffisante : augmenter org.gradle.jvmargs."),
]

TEST_PATTERNS: list[tuple[str, str, int, str]] = [
    (r"assertionerror|expected:.*but was:", "assertion", 2,
     "Assertion en échec. Se demander d'abord si le TEST a raison : ne jamais "
     "affaiblir une assertion pour obtenir du vert (§19)."),
    (r"nullpointerexception", "npe", 1,
     "NullPointerException : nullabilité mal gérée."),
    (r"aeadbadtag|badpadding|invalidkey|shortbuffer", "crypto", 1,
     "Échec d'authentification cryptographique : clé, IV, AAD ou tag incorrects."),
    (r"illegalstateexception|illegalargumentexception", "state", 2,
     "État ou argument invalide."),
    (r"sqliteexception|database.*(?:locked|corrupt)", "sqlite", 1,
     "Erreur SQLite : schéma, migration ou accès concurrent."),
    (r"filenotfoundexception|nosuchfileexception", "file", 2,
     "Fichier introuvable : chemin relatif dépendant du répertoire d'exécution."),
]

ALL_PATTERNS = (
    [(p, c, pr, a, "kotlin") for p, c, pr, a in KOTLIN_PATTERNS]
    + [(p, c, pr, a, "ksp") for p, c, pr, a in KSP_PATTERNS]
    + [(p, c, pr, a, "gradle") for p, c, pr, a in GRADLE_PATTERNS]
    + [(p, c, pr, a, "test") for p, c, pr, a in TEST_PATTERNS]
)

# `e: file:///chemin/Fichier.kt:12:34 message`
RE_KOTLIN = re.compile(r"^e:\s*(?:file://)?([^\s:]+\.kts?):(\d+):(\d+)\s*(.*)$", re.M)
RE_GENERIC = re.compile(r"^(?:\s*)(?:ERROR|error):\s*(.+)$", re.M)
RE_TEST_FAIL = re.compile(r"^(.+?)\s*>\s*(.+?)\s+FAILED\s*$", re.M)


def _classify(message: str) -> tuple[str, str, int, str]:
    low = message.lower()
    for pattern, category, priority, advice, kind in ALL_PATTERNS:
        if re.search(pattern, low):
            return category, kind, priority, advice
    return "unknown", "gradle", 50, "Motif non reconnu : analyse manuelle requise."


def parse(log: str) -> tuple[list[BuildError], list[RootCause]]:
    """Extrait les erreurs d'un journal et les regroupe par cause racine."""
    errors: list[BuildError] = []
    seen: set[tuple] = set()

    for m in RE_KOTLIN.finditer(log):
        path, line, col, msg = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4).strip()
        key = (path, line, col, msg)
        if key in seen:
            continue
        seen.add(key)
        _, kind, _, _ = _classify(msg)
        errors.append(BuildError(path, line, col, msg, kind, m.group(0)))

    for m in RE_TEST_FAIL.finditer(log):
        cls, test = m.group(1).strip(), m.group(2).strip()
        # La ligne « FAILED » ne dit pas POURQUOI. La cause est dans les lignes
        # suivantes (exception, assertion) : les joindre au message, sinon toute
        # défaillance de test serait classée « unknown ».
        tail = log[m.end():m.end() + 400]
        detail = ""
        for ln in tail.splitlines()[:6]:
            if re.search(r"(Exception|Error|expected:|assert)", ln, re.I):
                detail = ln.strip()
                break
        msg = f"test en échec : {test}" + (f" — {detail}" if detail else "")
        errors.append(BuildError(cls, 0, 0, msg, "test", m.group(0)))

    for m in RE_GENERIC.finditer(log):
        msg = m.group(1).strip()
        if any(msg in e.message for e in errors):
            continue
        _, kind, _, _ = _classify(msg)
        errors.append(BuildError("", 0, 0, msg, kind, m.group(0)))

    # --- regroupement par cause racine
    groups: dict[str, RootCause] = {}
    for err in errors:
        category, _, priority, advice = _classify(err.message)
        rc = groups.setdefault(category, RootCause(category, category, [], [], priority, advice))
        rc.errors.append(err)

    # Les `unresolved reference` pointant vers un fichier déjà en erreur pour une
    # autre cause sont des CONSÉQUENCES : elles disparaîtront d'elles-mêmes (§19.3).
    faulty_files = {
        e.file for cat, rc in groups.items() if cat != "unresolved" for e in rc.errors if e.file
    }
    # Noms des types déclarés par les fichiers en erreur : une référence non
    # résolue vers l'un d'eux est une conséquence, pas une cause. Sur ce projet,
    # une seule erreur dans BackupManager.kt casse MainRepository, MainViewModel
    # et SettingsScreen — soit 3 fausses pistes si on ne les écarte pas.
    faulty_symbols = {
        f.rsplit("/", 1)[-1].removesuffix(".kt").lower() for f in faulty_files
    }
    if "unresolved" in groups:
        rc = groups["unresolved"]
        primary, derived = [], []
        for e in rc.errors:
            symbol = re.search(r"unresolved reference:?\s*(\w+)", e.message, re.I)
            same_file = e.file in faulty_files
            from_faulty = bool(symbol) and symbol.group(1).lower() in faulty_symbols
            (derived if same_file or from_faulty else primary).append(e)
        rc.errors, rc.derived = primary, derived
        if not primary and derived:
            rc.priority = 90
            rc.advice = ("Toutes ces références non résolues proviennent de fichiers "
                         "déjà en erreur : elles se résoudront seules.")

    ordered = sorted(groups.values(), key=lambda r: (r.priority, -r.total))
    return errors, ordered


def estimate_saved_cycles(causes: list[RootCause]) -> int:
    """
    Cycles économisés en corrigeant toutes les causes indépendantes d'un coup
    plutôt qu'une par une (§19.4).
    """
    independent = [c for c in causes if c.errors and c.priority < 90]
    return max(0, len(independent) - 1)
