# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire AI-DOS post-commit pour J7.5b / Lot 2 (Configuration Gradle de signature conditionnelle Release). Aucune implémentation de code en cours.

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `8f391bca2bf791a48cc7dd67b34704cadb2febd4` |
| Parent | `dcd5f6c6aac97823d8511608b07c5ac031db83c3` (`docs(ai-dos): update J7.5a gitignore release secrets protection documentation`) |
| Commit technique J7.5b / Lot 2a | `8f391bca2bf791a48cc7dd67b34704cadb2febd4` (`build(gradle): configure conditional release signing and keystore template`) |
| J7.5a / Lot 1 (.gitignore) | TERMINÉ & COMMITTÉ (`3bd5c5a` / `dcd5f6c`) |
| J7.5b / Lot 2 (Configuration Gradle) | **TERMINÉ TECHNIQUEMENT & POST-AUDITÉ** (`8f391bc`, `GO`) |
| J7.5c / Lot 3 (Génération Keystore Local) | **BLOQUÉ / EN ATTENTE** (requiert la fin de la sync doc J7.5b et autorisation explicite) |

## J7.5b — Lot 2 : Configuration Gradle de signature conditionnelle Release (terminé)

Commit technique : `8f391bca2bf791a48cc7dd67b34704cadb2febd4`.
- **Fichiers modifiés / créés :**
  1. `CAISSE/app/build.gradle.kts` (+48 insertions) : Ajout du chargement conditionnel de `app/keystore.properties` avec validation des 4 propriétés obligatoires (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) et exception Fail-Fast `InvalidUserDataException` si incomplet ou si le fichier `.jks` est introuvable sur disque.
  2. `CAISSE/app/keystore.properties.example` (+13 insertions) : Création du gabarit de documentation versionné contenant les clés d'exemple avec des valeurs factices.
- **Comportement des États A à E vérifié :**
  - **État A (Fichier absent)** : `isReleaseSigningConfigured = false`. Log d'avertissement Gradle. Build Release assemblé **NON SIGNÉ (NON PUBLIABLE)**. Aucun repli vers la clé Debug.
  - **État B (Fichier présent & valide)** : `isReleaseSigningConfigured = true`. Bloc `signingConfigs.release` instancié et lié à `buildTypes.release.signingConfig`. Build Release **SIGNÉ**.
  - **État C (Fichier incomplet)** : Exception `InvalidUserDataException` levée -> Échec immédiat (Fail-Fast).
  - **État D (Keystore introuvable)** : Exception `InvalidUserDataException` levée -> Échec immédiat (Fail-Fast).
  - **État E (Mot de passe/alias invalide)** : Échec cryptographique lors de l'assemblage AGP.
- **Invariants :** Zero modification de code applicatif métier, zero modification CI, zero secret versionné dans Git.
- **Niveau de preuve :** Audits pré-commit, post-commit et Red Team validés avec le verdict `GO`.

Phase actuelle = **synchronisation documentaire AI-DOS J7.5b / Lot 2**.

## Prochaines étapes

- Revue du diff documentaire `.ai/`, staging contrôlé des 4 fichiers, audit pré-commit indépendant, puis autorisation du commit documentaire J7.5b / Lot 2b.
- Cadrage et autorisation explicite pour le démarrage du Lot J7.5c (Génération locale Keystore & Validation d'Artefact).

---
*Mis à jour le 2026-10-16 (post-commit audit GO J7.5b Lot 2). Précédente fiche : 2026-10-15, J7.5a Lot 1.*
