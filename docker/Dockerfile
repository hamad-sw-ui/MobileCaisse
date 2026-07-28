# syntax=docker/dockerfile:1.7
###############################################################################
# MobileCaisse — image de build Android reproductible
#
# Contraintes déduites du dépôt (ne pas modifier sans mettre à jour .ai/) :
#   - gradle/gradle-daemon-jvm.properties : toolchainVersion = 21  -> JDK 21 OBLIGATOIRE
#   - app/build.gradle.kts : jvmTarget = 11, compileSdk 35, minSdk 24
#   - gradle-wrapper.properties : Gradle 9.5.0 (téléchargé par le wrapper)
#   - AGP 8.13.2 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Room 2.7.0-alpha12
#
# Le JDK est la seule contrainte non négociable de l'image : Gradle refuse de
# démarrer son daemon sans JDK 21. Gradle lui-même n'est PAS installé : le
# wrapper le télécharge et le met en cache dans le volume gradle-cache.
###############################################################################

FROM eclipse-temurin:21-jdk-noble

LABEL org.opencontainers.image.title="mobilecaisse-android-build"
LABEL org.opencontainers.image.description="Environnement reproductible de build, analyse et test pour MobileCaisse"
LABEL org.opencontainers.image.source="https://github.com/hamad-sw-ui/MobileCaisse"

# --- Versions épinglées (reproductibilité) -----------------------------------
ARG ANDROID_CMDLINE_TOOLS_VERSION=13114758
ARG ANDROID_COMPILE_SDK=35
ARG ANDROID_BUILD_TOOLS=35.0.0
ARG BUNDLETOOL_VERSION=1.17.2
ARG KTLINT_VERSION=1.5.0
ARG DETEKT_VERSION=1.23.7
ARG JACOCO_VERSION=0.8.12

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_USER_HOME=/opt/android-sdk/.android \
    GRADLE_USER_HOME=/home/builder/.gradle \
    TOOLS_HOME=/opt/tools

# --- Paquets système ---------------------------------------------------------
# git : exigé par la spec Phase 6 (et par certains plugins Gradle)
# unzip/zip : cmdline-tools, bundletool, inspection d'APK/AAB
# ca-certificates/curl : téléchargements HTTPS
# gettext-base : envsubst, utilisé par les scripts de rapport
RUN apt-get update && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        file \
        git \
        gettext-base \
        jq \
        unzip \
        zip \
    && rm -rf /var/lib/apt/lists/*

# --- Utilisateur non-root ----------------------------------------------------
# UID/GID surchargeables au build pour que les fichiers écrits dans le volume
# monté appartiennent à l'utilisateur hôte (évite les fichiers root:root).
ARG USER_UID=1000
ARG USER_GID=1000
RUN groupadd -g "${USER_GID}" builder 2>/dev/null || groupmod -n builder "$(getent group ${USER_GID} | cut -d: -f1)" \
    && useradd -m -u "${USER_UID}" -g "${USER_GID}" -s /bin/bash builder 2>/dev/null \
       || usermod -l builder -d /home/builder -m "$(getent passwd ${USER_UID} | cut -d: -f1)"

# --- Android SDK -------------------------------------------------------------
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline \
    && mv /tmp/cmdline/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${TOOLS_HOME}/bin:${PATH}"

# Licences acceptées explicitement (build non interactif).
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && rm -rf "${ANDROID_HOME}/.android" \
    && mkdir -p "${ANDROID_USER_HOME}"

# --- Outils de qualité (CLI autonomes) ---------------------------------------
# Le projet ne déclare AUCUN plugin Gradle ktlint/detekt/jacoco (vérifié dans
# build.gradle.kts et libs.versions.toml). Ces outils sont donc fournis en CLI
# autonome : ils fonctionnent sans modifier le build du projet.
# Voir .ai/BACKLOG.md B-097 (plugins Gradle) et B-099 (JaCoCo).
RUN mkdir -p "${TOOLS_HOME}/bin" "${TOOLS_HOME}/lib" \
    # ktlint
    && curl -fsSL -o "${TOOLS_HOME}/bin/ktlint" \
        "https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint" \
    && chmod +x "${TOOLS_HOME}/bin/ktlint" \
    # detekt
    && curl -fsSL -o /tmp/detekt.zip \
        "https://github.com/detekt/detekt/releases/download/v${DETEKT_VERSION}/detekt-cli-${DETEKT_VERSION}.zip" \
    && unzip -q /tmp/detekt.zip -d "${TOOLS_HOME}/lib" \
    && ln -s "${TOOLS_HOME}/lib/detekt-cli-${DETEKT_VERSION}/bin/detekt-cli" "${TOOLS_HOME}/bin/detekt" \
    && rm -f /tmp/detekt.zip \
    # bundletool
    && curl -fsSL -o "${TOOLS_HOME}/lib/bundletool.jar" \
        "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar" \
    && printf '#!/usr/bin/env bash\nexec java -jar %s/lib/bundletool.jar "$@"\n' "${TOOLS_HOME}" > "${TOOLS_HOME}/bin/bundletool" \
    && chmod +x "${TOOLS_HOME}/bin/bundletool" \
    # jacoco (CLI : fusion et génération de rapports)
    && curl -fsSL -o /tmp/jacoco.zip \
        "https://repo1.maven.org/maven2/org/jacoco/jacoco/${JACOCO_VERSION}/jacoco-${JACOCO_VERSION}.zip" \
    && unzip -q /tmp/jacoco.zip -d "${TOOLS_HOME}/lib/jacoco" \
    && printf '#!/usr/bin/env bash\nexec java -jar %s/lib/jacoco/lib/jacococli.jar "$@"\n' "${TOOLS_HOME}" > "${TOOLS_HOME}/bin/jacococli" \
    && chmod +x "${TOOLS_HOME}/bin/jacococli" \
    && rm -f /tmp/jacoco.zip

# Kotlin : le compilateur est fourni par le plugin Gradle Kotlin 2.1.0 (embarqué
# dans la build). Aucun kotlinc système n'est installé pour éviter toute
# divergence de version avec celui utilisé réellement par le build.

# --- Permissions & répertoires -----------------------------------------------
RUN mkdir -p /home/builder/.gradle /workspace \
    && chown -R builder:builder /home/builder "${ANDROID_HOME}" "${TOOLS_HOME}" /workspace

# Le projet est monté ici (volume), jamais copié : l'image reste indépendante
# du code et n'a pas besoin d'être reconstruite à chaque modification.
WORKDIR /workspace

USER builder

# Réglages Gradle propres au conteneur.
# - pas de daemon : chaque exécution est isolée (exigence « environnement propre »)
# - Xmx2g : cohérent avec gradle.properties du projet
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8"

COPY --chown=builder:builder scripts/entrypoint.sh /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["bash"]
