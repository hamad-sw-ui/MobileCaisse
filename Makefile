# MobileCaisse — raccourcis vers l'environnement Docker (Phase 6).
# Toutes les cibles délèguent aux scripts de docker/scripts/.
#
#   make help

.DEFAULT_GOAL := help
SHELL := /bin/bash

.PHONY: help image verify build build-release test lint validate \
        shell sandbox sandbox-shell apk aab instrumented clean clean-caches clean-all

help: ## Affiche cette aide
	@echo "MobileCaisse — environnement Docker"
	@echo
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "Première utilisation :  make image && make verify"
	@echo "Avant chaque commit  :  make validate"

image: ## Construire l'image Docker
	@./docker/scripts/build-image.sh

image-fresh: ## Reconstruire l'image sans cache
	@./docker/scripts/build-image.sh --no-cache

verify: ## Valider l'environnement (à faire AVANT toute modification)
	@./docker/scripts/verify-env.sh

build: ## Compiler (debug)
	@./docker/scripts/build.sh debug

build-release: ## Compiler (release)
	@./docker/scripts/build.sh release

test: ## Tests unitaires (JVM, dans Docker)
	@./docker/scripts/test.sh

lint: ## Analyses statiques (Android Lint + ktlint + detekt)
	@./docker/scripts/lint.sh

validate: ## CHAÎNE COMPLÈTE : env + compilation + analyses + tests
	@./docker/scripts/validate.sh

shell: ## Shell interactif de développement
	@./docker/scripts/shell.sh

sandbox: ## Expérimentation isolée (conteneur jetable, projet en lecture seule)
	@./docker/scripts/sandbox.sh

sandbox-shell: ## Shell jetable, projet en lecture seule
	@./docker/scripts/sandbox.sh --shell

apk: ## Générer un APK debug
	@./docker/scripts/package.sh apk-debug

aab: ## Générer un AAB release (+ inspection bundletool)
	@./docker/scripts/package.sh aab

instrumented: ## Tests instrumentés (HORS Docker — appareil requis)
	@./docker/scripts/test-instrumented.sh

clean: ## Nettoyer conteneurs et artefacts de build
	@./docker/scripts/clean.sh

clean-caches: ## + supprimer les volumes de cache
	@./docker/scripts/clean.sh --caches

clean-all: ## + supprimer l'image Docker
	@./docker/scripts/clean.sh --all
