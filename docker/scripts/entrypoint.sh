#!/usr/bin/env bash
# Point d'entrée du conteneur de build MobileCaisse.
# Vérifie que l'environnement est cohérent AVANT d'exécuter quoi que ce soit.
set -euo pipefail

fail() { echo "❌ $*" >&2; exit 1; }

# --- Contrôles d'environnement (échec rapide et explicite) -------------------
[[ -d /workspace ]] || fail "/workspace absent : le projet n'est pas monté."

if [[ ! -f /workspace/settings.gradle.kts ]]; then
  fail "/workspace ne contient pas settings.gradle.kts.
       Le dépôt doit être monté à la racine de /workspace.
       Lancez les commandes depuis la racine du projet."
fi

# Le wrapper Gradle est fréquemment non exécutable dans ce dépôt (BUG-016 / B-001).
# On ne modifie PAS le fichier (il est monté depuis l'hôte) : on invoque le
# wrapper via `sh gradlew`, ce qui fonctionne quel que soit le bit d'exécution.
if [[ -f /workspace/gradlew && ! -x /workspace/gradlew ]]; then
  echo "⚠️  gradlew n'est pas exécutable sur l'hôte (voir BACKLOG B-001)."
  echo "    Les scripts utilisent 'sh gradlew' : le build fonctionne malgré tout."
fi

# JDK 21 est exigé par gradle/gradle-daemon-jvm.properties (toolchainVersion=21).
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "${JAVA_MAJOR}" != "21" ]]; then
  fail "JDK 21 attendu (toolchainVersion=21), trouvé : ${JAVA_MAJOR}."
fi

[[ -d "${ANDROID_HOME}" ]] || fail "ANDROID_HOME introuvable : ${ANDROID_HOME}"

# local.properties : ignoré par Git, il peut contenir un sdk.dir de l'hôte
# (chemin Windows/macOS) qui casserait le build dans le conteneur.
if [[ -f /workspace/local.properties ]] && grep -q '^sdk.dir' /workspace/local.properties; then
  echo "⚠️  local.properties contient un 'sdk.dir' provenant de l'hôte."
  echo "    Dans le conteneur, ANDROID_HOME=${ANDROID_HOME} fait foi."
fi

exec "$@"
