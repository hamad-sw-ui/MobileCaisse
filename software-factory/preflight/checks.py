#!/usr/bin/env python3
"""
Contrôles statiques exécutables sans JDK ni SDK Android.

Chaque contrôle est une fonction `check_*(ctx) -> list[Finding]`.
Ajouter un contrôle = ajouter une fonction et l'inscrire dans ALL_CHECKS.

Règle de conception : **aucun faux positif toléré**. Un outil qui crie au loup
est désactivé par ses utilisateurs. En cas de doute sur un motif, ne rien
signaler plutôt que signaler à tort.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Finding:
    severity: str          # "error" | "warning" | "info"
    check: str
    message: str
    file: str = ""
    line: int = 0

    def __str__(self) -> str:
        loc = f"{self.file}:{self.line}" if self.file else ""
        return f"[{self.check}] {loc} {self.message}".strip()


@dataclass
class Context:
    root: Path
    kt_main: list[Path] = field(default_factory=list)
    kt_test: list[Path] = field(default_factory=list)
    manifest: Path | None = None

    @classmethod
    def build(cls, root: Path) -> "Context":
        app = root / "app" / "src"
        ctx = cls(root=root)
        if (app / "main").exists():
            ctx.kt_main = sorted((app / "main").rglob("*.kt"))
        for sub in ("test", "androidTest"):
            if (app / sub).exists():
                ctx.kt_test += sorted((app / sub).rglob("*.kt"))
        mf = app / "main" / "AndroidManifest.xml"
        ctx.manifest = mf if mf.exists() else None
        return ctx

    def rel(self, p: Path) -> str:
        try:
            return str(p.relative_to(self.root))
        except ValueError:
            return str(p)


# ---------------------------------------------------------------- utilitaires

def strip_kotlin(src: str) -> str:
    """
    Retire commentaires et littéraux d'une source Kotlin, en préservant la
    structure des délimiteurs.

    Les trois faux positifs rencontrés en session manuelle venaient tous d'un
    filtrage incomplet : chaines brutes contenant des accolades de regex,
    interpolation, et commentaires citant du code. Ce filtrage les traite tous.
    """
    # Ordre impératif : les littéraux DOIVENT être neutralisés avant les
    # commentaires. Sinon une chaîne contenant "//" — cas réel rencontré dans
    # BackupManagerTest — tronque la ligne et déséquilibre le comptage.
    src = re.sub(r'""".*?"""', '""', src, flags=re.S)          # chaînes brutes
    src = re.sub(r'"(\\.|\$\{[^}]*\}|[^"\\\n])*"', '""', src)  # chaînes + interpolation
    src = re.sub(r"'(\\.|[^'\\\n])*'", "''", src)              # caractères
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)            # blocs
    src = re.sub(r'//[^\n]*', '', src)                         # lignes
    return src


def code_only(src: str) -> str:
    """Code sans commentaires, en conservant les littéraux (recherche de motifs)."""
    # Les lignes retirées sont remplacées par du vide plutôt que supprimées :
    # sinon la numérotation se décale et les positions signalées sont fausses.
    src = re.sub(r'/\*.*?\*/', lambda m: "\n" * m.group(0).count("\n"), src, flags=re.S)
    lines = []
    for ln in src.split("\n"):
        st = ln.lstrip()
        if st.startswith("*") or st.startswith("//"):
            lines.append("")
        else:
            lines.append(ln.split("//")[0])
    return "\n".join(lines)


def line_of(src: str, index: int) -> int:
    return src.count("\n", 0, index) + 1


# ------------------------------------------------------------------ contrôles

def check_delimiters(ctx: Context) -> list[Finding]:
    """Délimiteurs déséquilibrés — cause d'échec de compilation la plus banale."""
    out = []
    for p in ctx.kt_main + ctx.kt_test:
        s = strip_kotlin(p.read_text(encoding="utf-8", errors="replace"))
        for op, cl, name in (("{", "}", "accolades"), ("(", ")", "parenthèses"),
                             ("[", "]", "crochets")):
            d = s.count(op) - s.count(cl)
            if d:
                out.append(Finding(
                    "error", "delimiters",
                    f"{name} déséquilibrées (écart {d:+d})", ctx.rel(p)))
    return out


def check_min_sdk_apis(ctx: Context) -> list[Finding]:
    """
    API indisponibles sous minSdk 24 (BUG-020).

    Analyse le **code seul** : les commentaires citent légitimement ces API pour
    expliquer pourquoi elles sont bannies — c'était le faux positif CR-2.
    """
    banned = {
        "java.time.": "API 26 — utiliser java.util.Date / SimpleDateFormat",
        "java.util.Base64": "API 26 — utiliser un encodage maison ou android.util.Base64",
        ".readAllBytes()": "API 33 — copier via un ByteArrayOutputStream",
        "java.nio.file.": "API 26 — utiliser java.io.File",
    }
    out = []
    for p in ctx.kt_main:
        code = code_only(p.read_text(encoding="utf-8", errors="replace"))
        for pattern, advice in banned.items():
            idx = code.find(pattern)
            if idx >= 0:
                out.append(Finding(
                    "error", "min-sdk",
                    f"`{pattern}` incompatible minSdk 24 — {advice}",
                    ctx.rel(p), line_of(code, idx)))
    return out


def check_fileprovider_authority(ctx: Context) -> list[Finding]:
    """
    Autorité FileProvider divergente du manifeste.

    Ce contrôle aurait attrapé BUG-025 (`.provider` au lieu de `.fileprovider`),
    qui provoquait un crash au partage d'un relevé client.
    """
    if not ctx.manifest:
        return []
    mf = ctx.manifest.read_text(encoding="utf-8", errors="replace")
    m = re.search(r'android:authorities="\$\{applicationId\}\.([\w.]+)"', mf)
    if not m:
        return []
    expected = m.group(1)

    out = []
    for p in ctx.kt_main:
        src = p.read_text(encoding="utf-8", errors="replace")
        for hit in re.finditer(r'getUriForFile\s*\([^,]+,\s*"([^"]+)"', src):
            authority = hit.group(1)
            suffix = authority.rsplit("}", 1)[-1].lstrip(".")
            if suffix and suffix != expected:
                out.append(Finding(
                    "error", "fileprovider",
                    f'autorité "{suffix}" ≠ manifeste "{expected}" — '
                    f"IllegalArgumentException au runtime",
                    ctx.rel(p), line_of(src, hit.start())))
    return out


def check_missing_resources(ctx: Context) -> list[Finding]:
    """
    Ressources `R.xxx.yyy` référencées mais absentes.

    Exclut `android.R.*` : ce sont des ressources système — c'était le faux
    positif FP-1 de la session manuelle.
    """
    res = ctx.root / "app" / "src" / "main" / "res"
    if not res.exists():
        return []

    declared: set[tuple[str, str]] = set()
    for values in res.glob("values*/*.xml"):
        txt = values.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r'<(\w+)[^>]*\bname="([\w.]+)"', txt):
            tag, name = m.group(1), m.group(2)
            kind = {"string": "string", "color": "color", "style": "style",
                    "dimen": "dimen", "integer": "integer", "bool": "bool",
                    "plurals": "plurals", "string-array": "array",
                    "array": "array"}.get(tag)
            if kind:
                declared.add((kind, name))
    for d in res.iterdir():
        if not d.is_dir():
            continue
        kind = d.name.split("-")[0]
        if kind in ("drawable", "mipmap", "raw", "font", "anim", "xml"):
            for f in d.iterdir():
                declared.add((kind, f.name.split(".")[0]))

    out = []
    for p in ctx.kt_main:
        src = p.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r'(?<!android\.)\bR\.(\w+)\.(\w+)', src):
            kind, name = m.group(1), m.group(2)
            if kind == "id":       # générés par les layouts, hors périmètre
                continue
            if (kind, name) not in declared:
                out.append(Finding(
                    "error", "resources",
                    f"R.{kind}.{name} introuvable dans res/",
                    ctx.rel(p), line_of(src, m.start())))
    return out


def check_duplicate_resources(ctx: Context) -> list[Finding]:
    """Fichiers de ressources identiques — CR-1 : doublon de 861 Ko."""
    res = ctx.root / "app" / "src" / "main" / "res"
    if not res.exists():
        return []
    by_size: dict[int, list[Path]] = {}
    for d in res.iterdir():
        if d.is_dir() and d.name.split("-")[0] in ("drawable", "mipmap", "raw"):
            for f in d.iterdir():
                if f.is_file() and f.stat().st_size > 10_000:
                    by_size.setdefault(f.stat().st_size, []).append(f)

    out = []
    for size, files in by_size.items():
        if len(files) < 2:
            continue
        seen: dict[bytes, Path] = {}
        for f in files:
            digest = f.read_bytes()
            if digest in seen:
                out.append(Finding(
                    "warning", "duplicate-res",
                    f"identique à {ctx.rel(seen[digest])} ({size // 1024} Ko dupliqués)",
                    ctx.rel(f)))
            else:
                seen[digest] = f
    return out


def check_room_migrations(ctx: Context) -> list[Finding]:
    """
    Divergences entre le SQL des migrations et les `@Entity` (BUG-001).

    Contrôle **indicatif** : signale les colonnes créées par une migration mais
    absentes de l'entité correspondante. Seul `exportSchema = true` + Room
    peuvent trancher définitivement.
    """
    entity_dir = ctx.root / "app/src/main/java/com/reconsiliation/caisse/data/local/entity"
    db_file = ctx.root / "app/src/main/java/com/reconsiliation/caisse/data/local/AppDatabase.kt"
    if not entity_dir.exists() or not db_file.exists():
        return []

    tables: dict[str, set[str]] = {}
    for p in entity_dir.glob("*.kt"):
        src = p.read_text(encoding="utf-8", errors="replace")
        m = re.search(r'tableName\s*=\s*"(\w+)"', src)
        if not m:
            continue
        body = src[src.find("data class"):]
        cols = set(re.findall(r'\bval\s+(\w+)\s*:', body))
        tables[m.group(1)] = cols

    out = []
    db = db_file.read_text(encoding="utf-8", errors="replace")
    for m in re.finditer(r'CREATE TABLE IF NOT EXISTS `(\w+)` \((.*?)\)"\)', db, re.S):
        table, cols_sql = m.group(1), m.group(2)
        if table not in tables:
            continue
        declared = set(re.findall(r'`(\w+)`\s+(?:INTEGER|TEXT|REAL|DOUBLE|BLOB)', cols_sql))
        extra = declared - tables[table] - {"id"}
        missing = tables[table] - declared - {"id"}
        if extra or missing:
            detail = []
            if extra:
                detail.append(f"en trop dans la migration : {', '.join(sorted(extra))}")
            if missing:
                detail.append(f"absentes de la migration : {', '.join(sorted(missing))}")
            out.append(Finding(
                "warning", "room-migration",
                f"table `{table}` — {' ; '.join(detail)}",
                ctx.rel(db_file), line_of(db, m.start())))
    return out


def check_coding_rules(ctx: Context) -> list[Finding]:
    """Violations de CODING_RULES détectables statiquement."""
    out = []
    for p in ctx.kt_main:
        src = p.read_text(encoding="utf-8", errors="replace")
        code = code_only(src)
        rel = ctx.rel(p)

        # `[ \t]*\n?[ \t]*` : tolère un unique retour à la ligne, pas davantage.
        for m in re.finditer(r'catch\s*\([^)]*\)[ \t]*\{[ \t]*\n?[ \t]*\}', code):
            out.append(Finding("warning", "empty-catch",
                               "bloc catch vide — §5 : logger et remonter l'erreur",
                               rel, line_of(code, m.start())))

        for m in re.finditer(r'\bGlobalScope\b', code):
            out.append(Finding("error", "global-scope",
                               "GlobalScope interdit — §5 : concurrence structurée",
                               rel, line_of(code, m.start())))

        if "/ui/" in str(p).replace("\\", "/") and "/ui/viewmodel/" not in str(p).replace("\\", "/"):
            for m in re.finditer(r'AppDatabase\.getDatabase|getDatabasePath', code):
                out.append(Finding("warning", "db-in-ui",
                                   "accès base depuis l'UI — §1 : passer par le ViewModel",
                                   rel, line_of(code, m.start())))
    return out


ALL_CHECKS = [
    check_delimiters,
    check_min_sdk_apis,
    check_fileprovider_authority,
    check_missing_resources,
    check_duplicate_resources,
    check_room_migrations,
    check_coding_rules,
]
