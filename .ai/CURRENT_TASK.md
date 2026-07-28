# 🎯 TÂCHE EN COURS

**Tâche :**
**Jalon 0 — Rendre le build vérifiable et nettoyer le dépôt** (décision D3),
enrichi de **B-110** (renommage honnête « Exporter et partager », décision D2
volet 1).

Sous-tâches, dans cet ordre :
| # | Réf. | Contenu |
|---|---|---|
| 0.1 | B-001 | Rendre `gradlew` exécutable (`git update-index --chmod=+x`) |
| 0.2 | B-002 | Créer `app/proguard-rules.pro` (référencé dans le build, absent du dépôt) |
| 0.3 | B-003 | Résoudre `androidx.appcompat` : le déclarer, ou migrer vers `ComponentActivity` + `Theme.Material3` |
| 0.4 | B-004 | Rapatrier `sqlcipher` et `sqlite-ktx` dans `libs.versions.toml` |
| 0.5 | B-005/B-006 | Aligner la version Kotlin, stabiliser `configuration-cache` |
| 0.6 | B-007/B-008 | Supprimer `conversation.txt`, dé-versionner `.idea/` et `.kotlin/`, déplacer `app/logo.png`, compléter `.gitignore` |
| 0.7 | B-100 | Rédiger le `README.md` racine |
| 0.8 | B-110 | Renommer « Synchronisation cloud » → « Exporter et partager » (libellés UI + `logAction`), **sans toucher à la logique** |

**Objectif :**
Obtenir un dépôt propre dont le build est **reproductible et prouvé** par
`./gradlew assembleDebug` et `./gradlew test`, condition sine qua non pour
attaquer J1 (intégrité des migrations) avec des résultats vérifiables.

**Contraintes :**
- **Aucune régression fonctionnelle** : J0 ne touche à aucune logique métier.
- **Ne jamais régresser les 5 correctifs de sécurité déjà en place** :
  pas de retour de `MASTER_EMERGENCY_2024`, pas de
  `fallbackToDestructiveMigration()`, pas de syntaxe SQL brute `x'...'`,
  ne pas affaiblir `SecurityUtil` ni `BackupManager`.
- B-110 est **cosmétique** : libellés et messages de journal uniquement.
  Le renommage des identifiants Kotlin (`syncToCloud`) est **en attente
  d'arbitrage** (question B du rapport `rapport_analyse_2026-07-28_sauvegarde_distante.md`).
- **B-003 exige un arbitrage** avant exécution : déclarer AppCompat (1 ligne,
  sans risque) ou migrer vers Material3 (plus propre, touche `MainActivity` et
  `themes.xml`). Je recommande la migration, le projet étant 100 % Compose.
- ⚠️ Mon environnement n'a **ni JDK ni SDK Android**. Conformément à **D4**, je
  prépare les modifications et fournis les **commandes exactes** ; le
  responsable exécute et me transmet les logs. Ne jamais affirmer qu'un code
  compile sans preuve.
- Respecter `CODING_RULES.md`, `ANDROID_RULES.md` et
  `CHECKLISTS/avant_commit.md`.

**Critère de sortie :**
`./gradlew assembleDebug` et `./gradlew test` réussissent, logs consignés dans
`REPORTS/`. Puis enchaînement sur **J1.1** (`exportSchema = true`) et **J1.2**
(harnais `MigrationTestHelper`).

---

*Mis à jour le 2026-07-28 (rév. 2 — décisions D1–D4 intégrées).*
