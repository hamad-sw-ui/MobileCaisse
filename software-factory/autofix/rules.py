#!/usr/bin/env python3
"""
SF-03 `autofix` — corrections automatiques **sûres** uniquement.

Une règle n'est admise ici que si elle satisfait les trois conditions :

1. **Déterministe** — une seule correction possible, sans jugement.
2. **Vérifiable** — le résultat se contrôle statiquement.
3. **Sans risque métier** — ne touche ni crypto, ni migration, ni logique
   financière.

Tout le reste est *proposé*, jamais appliqué. `CODING_RULES.md` §13 exige une
validation humaine sur les zones sensibles ; l'automatisation ne la contourne pas.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Fix:
    rule: str
    file: str
    line: int
    before: str
    after: str
    explanation: str
    applied: bool = False


# Zones où aucune correction automatique n'est autorisée, quelle que soit la
# règle. La liste est volontairement large : le coût d'une correction fautive y
# est sans commune mesure avec le temps gagné.
FORBIDDEN_PATHS = (
    "utils/SecurityUtil.kt",
    "utils/BackupManager.kt",
    "utils/LicenseUtil.kt",
    "data/local/AppDatabase.kt",
    "data/local/entity/",
)


def is_protected(path: str) -> bool:
    norm = path.replace("\\", "/")
    return any(f in norm for f in FORBIDDEN_PATHS)


# ----------------------------------------------------------------- règles

def fix_fileprovider_authority(path: Path, src: str, manifest_authority: str) -> list[Fix]:
    """
    Aligne l'autorité FileProvider sur celle du manifeste.

    Déterministe : une seule valeur correcte, lue dans le manifeste.
    Précédent : BUG-025, découvert manuellement, crash au partage d'un relevé.
    """
    fixes = []
    for m in re.finditer(r'(getUriForFile\s*\([^,]+,\s*")([^"]+)(")', src):
        authority = m.group(2)
        suffix = authority.rsplit("}", 1)[-1].lstrip(".")
        if suffix and suffix != manifest_authority:
            corrected = authority.replace(suffix, manifest_authority)
            fixes.append(Fix(
                "fileprovider", str(path),
                src.count("\n", 0, m.start()) + 1,
                authority, corrected,
                f'autorité alignée sur le manifeste ("{manifest_authority}")'))
    return fixes


def fix_empty_catch(path: Path, src: str) -> list[Fix]:
    """
    Ajoute une trace dans un `catch` vide.

    ⚠️ **Proposition seulement, jamais appliquée.** Le traitement correct dépend
    du contexte : les 6 cas corrigés le 2026-07-30 appelaient cinq réponses
    différentes — trace simple, avertissement, message utilisateur, repli
    volontaire explicité. Un `Log.e` générique masquerait le vrai besoin.
    """
    fixes = []
    for m in re.finditer(r'catch\s*\((\w+):\s*\w+\)[ \t]*\{[ \t]*\n?[ \t]*\}', src):
        fixes.append(Fix(
            "empty-catch", str(path),
            src.count("\n", 0, m.start()) + 1,
            m.group(0).strip(), "",
            "catch vide — le traitement dépend du contexte, correction manuelle requise"))
    return fixes


def fix_unused_imports(path: Path, src: str) -> list[Fix]:
    """
    Supprime un import dont le symbole n'apparaît nulle part ailleurs.

    Déterministe et vérifiable. Les imports génériques (`.*`) sont ignorés :
    impossible de savoir ce qu'ils fournissent sans résoudre le classpath.
    """
    fixes = []
    body_start = 0
    for m in re.finditer(r'^import\s+([\w.]+)$', src, re.M):
        body_start = max(body_start, m.end())

    body = src[body_start:]
    body = re.sub(r'//[^\n]*', '', body)

    # Symboles utilisés IMPLICITEMENT par le compilateur : jamais nommés dans le
    # code, mais indispensables. Les supprimer casse la compilation.
    # Détecté avant application le 2026-07-30 : `getValue`/`setValue` sont les
    # opérateurs de délégation de `by remember`.
    implicit = {
        "getValue", "setValue", "provideDelegate",  # délégation de propriété
        "invoke", "component1", "component2",       # conventions d'opérateur
        "iterator", "compareTo", "contains",
        "plus", "minus", "times", "div", "rem",
        "inc", "dec", "unaryPlus", "unaryMinus",
    }

    for m in re.finditer(r'^import\s+([\w.]+)$', src, re.M):
        fqn = m.group(1)
        if fqn.endswith("*"):
            continue
        symbol = fqn.rsplit(".", 1)[-1]
        if symbol in implicit:
            continue
        # Un import d'extension de fonction peut être invoqué sans que son nom
        # apparaisse tel quel : ne rien supprimer qui commence par une minuscule
        # et ne ressemble donc pas à un type.
        if symbol[0].islower() and "." in fqn:
            continue
        if not re.search(rf'\b{re.escape(symbol)}\b', body):
            fixes.append(Fix(
                "unused-import", str(path),
                src.count("\n", 0, m.start()) + 1,
                m.group(0), "",
                f"import inutilisé : {symbol}"))
    return fixes


def fix_trailing_whitespace(path: Path, src: str) -> list[Fix]:
    """Espaces en fin de ligne. Sans risque : n'affecte pas la sémantique Kotlin."""
    fixes = []
    for i, line in enumerate(src.split("\n"), 1):
        if line != line.rstrip() and line.strip():
            fixes.append(Fix(
                "trailing-space", str(path), i, line, line.rstrip(),
                "espaces en fin de ligne"))
    return fixes


# Une règle n'est appliquée automatiquement que si elle figure ici.
AUTO_APPLICABLE = {"fileprovider", "unused-import", "trailing-space"}

# Les autres sont signalées pour traitement humain.
PROPOSE_ONLY = {"empty-catch"}
