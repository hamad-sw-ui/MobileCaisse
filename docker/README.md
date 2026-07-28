# 🐳 docker/ — Environnement de build MobileCaisse

Documentation complète : **[`.ai/DEV_ENVIRONMENT.md`](../.ai/DEV_ENVIRONMENT.md)**

## Démarrage rapide

```bash
make image     # construire l'image      (~5-10 min, une seule fois)
make verify    # valider l'environnement (AVANT toute modification)
make validate  # compilation + analyses + tests  (APRÈS chaque modification)
```

Sans `make` : `./docker/scripts/<script>.sh`.

## Scripts

| Script | Rôle |
|---|---|
| `build-image.sh` | Construire / reconstruire l'image (`--no-cache`) |
| `verify-env.sh` | Valider l'environnement — **obligatoire avant de coder** |
| `build.sh` | Compiler (`debug` / `release` / `aab` / `clean`) |
| `test.sh` | Tests unitaires JVM (+ filtre facultatif) |
| `lint.sh` | Android Lint + ktlint + detekt |
| `validate.sh` | **Chaîne complète** — s'arrête au premier échec |
| `sandbox.sh` | Expérimentation jetable, dépôt en lecture seule |
| `package.sh` | APK / AAB + inspection bundletool |
| `test-instrumented.sh` | ⚠️ Tests instrumentés — **hors Docker** |
| `shell.sh` | Shell de développement |
| `clean.sh` | Nettoyage (`--caches`, `--all`) |

## Ce qui tourne dans Docker, et ce qui n'y tourne pas

| Dans Docker ✅ | Hors Docker ❌ |
|---|---|
| Compilation | Tests instrumentés (Keystore matériel) |
| Tests unitaires | Émulateur Android (`/dev/kvm`) |
| Analyses statiques | Android Studio |
| APK / AAB | |

Justification détaillée : `.ai/DEV_ENVIRONMENT.md` §1 et §7.

## Prérequis hôte

Docker ≥ 24 et Docker Compose v2. **Rien d'autre** — ni JDK, ni Android SDK,
ni Gradle.

Les rapports sont écrits dans `.ai/REPORTS/`.
