# 🗺️ ROADMAP — Plan de complétion

> **Statut : EN ATTENTE DE VALIDATION** par le responsable du projet.
> Aucune ligne de code de production ne sera modifiée avant accord explicite.

Principe directeur : **on ne construit rien sur des fondations qui s'effondrent.**
L'ordre ci-dessous suit les dépendances techniques réelles, pas l'attrait des
fonctionnalités.

---

## Vue d'ensemble des dépendances

```
J0 Build vérifiable
      │  (sans compilation, aucune correction n'est prouvable)
      ▼
J1 Intégrité des données ──────────────► le plus urgent : BUG-001/002
      │  (migrations Room + sauvegarde fiable)
      ▼
J2 Sécurité ───────────────────────────► dépend de J1 (rekey ↔ migrations)
      │
      ▼
J3 Filet de tests ─────────────────────► indispensable avant tout refactor
      │
      ▼
J4 Hilt + assainissement ──────────────► dépend de J3 (non-régression)
      │
      ▼
J5 Découpage Repository / ViewModel ───► dépend de J4 (DI en place)
      │
      ▼
J6 Fonctionnalités manquantes ─────────► dépend de J5 (socle sain)
      │
      ▼
J7 Industrialisation (CI, release)
```

---

## Jalon 0 — Rendre le build vérifiable

**Pourquoi en premier** : aujourd'hui personne ne peut affirmer que le projet
compile. Sans build reproductible, toute correction est une hypothèse.

| Ordre | Tâche | Réf. |
|---|---|---|
| 0.1 | Rendre `gradlew` exécutable | B-001 |
| 0.2 | Créer `app/proguard-rules.pro` | B-002 |
| 0.3 | Résoudre `androidx.appcompat` (déclarer ou migrer vers Material3) | B-003 |
| 0.4 | Rapatrier `sqlcipher` / `sqlite-ktx` dans le version catalog | B-004 |
| 0.5 | Aligner la version Kotlin, stabiliser `configuration-cache` | B-005, B-006 |
| 0.6 | Nettoyer le dépôt (`conversation.txt`, `.idea/`, `.kotlin/`, `logo.png`, `.gitignore`) | B-007, B-008 |
| 0.7 | Rédiger le `README.md` racine | B-100 |

**Critère de sortie** : `./gradlew assembleDebug` et `./gradlew test` réussissent
sur une machine propre. Résultat consigné dans `REPORTS/`.

⚠️ **Dépendance externe** : l'environnement de travail actuel n'a **ni JDK ni SDK
Android**. Le jalon 0 exige soit un environnement outillé, soit une exécution du
build par le responsable qui me transmet les logs.

---

## Jalon 1 — Intégrité des données (priorité absolue)

**Pourquoi ici** : c'est le seul défaut qui **détruit de la valeur client**.
Un crash au démarrage sur un appareil contenant des mois de ventes est
irréparable sans serveur.

| Ordre | Tâche | Réf. |
|---|---|---|
| 1.1 | Activer `exportSchema = true`, générer et versionner `app/schemas/` | B-009 |
| 1.2 | Écrire le harnais `MigrationTestHelper` (avant de corriger, pour prouver l'échec) | B-012 |
| 1.3 | Réécrire les 6 migrations divergentes + migration corrective 28→29 | B-010 |
| 1.4 | Traiter les bases en version < 18 (décision produit requise) | B-011 |
| 1.5 | Checkpoint WAL avant toute copie de la base | B-013 |
| 1.6 | Retirer la contrainte réseau de `BackupWorker` | B-014 |
| 1.7 | Ajouter les index manquants (`vente_items.venteId`, `audit_items.auditId`) | B-057 |

**Critère de sortie** : tests de migration verts sur toute la chaîne 18→29 ;
sauvegarde/restauration validée manuellement sur un appareil réel.

**❓ Décision produit attendue (1.4)** : que faire d'une base en version < 18 ?
(a) écrire les migrations manquantes, (b) exporter puis recréer avec
consentement de l'utilisateur, (c) considérer ce cas comme inexistant si aucune
version publique n'a jamais dépassé la 18. **Votre réponse conditionne le
chiffrage.**

---

## Jalon 2 — Sécurité

**Pourquoi après J1** : le durcissement de la clé implique un **rekey** de la
base ; il doit s'appuyer sur une couche de migration déjà fiable.

| Ordre | Tâche | Réf. |
|---|---|---|
| 2.1 | Activer R8 + règles keep (préalable à toute obfuscation utile) | B-022 |
| 2.2 | Renforcer la dérivation de clé (PBKDF2 ≥ 100k + sel) | B-020 |
| 2.3 | Migration sans perte des bases existantes vers la nouvelle clé | B-021 |
| 2.4 | Sécuriser et centraliser `PRAGMA rekey` | B-025 |
| 2.5 | Durcir les PIN (itérations + re-hash paresseux) | B-028 |
| 2.6 | Retirer le générateur de licence de l'APK client | B-023 |
| 2.7 | Sortir `SECRET_SALT` du code clair | B-024 |
| 2.8 | Réserver le seeder au build debug | B-026 |
| 2.9 | Porter le rôle requis dans `Screen` | B-027 |
| 2.10 | Corriger la détection d'anomalie de date SMS | B-029 |

**Critère de sortie** : `REPORTS/rapport_securite_<date>.md` complet ; APK
release décompilé sans exposer de secret exploitable trivialement.

---

## Jalon 3 — Filet de tests

**Pourquoi avant le refactor** : sans tests, « ne jamais casser une
fonctionnalité existante » (MISSION §3) est un vœu pieux. Ce jalon cible
d'abord les zones **pures et à fort risque financier**.

| Ordre | Tâche | Réf. |
|---|---|---|
| 3.1 | Tests `SmsParser` (crée automatiquement des ventes → risque n°1) | B-090 |
| 3.2 | Tests `FeeCalculator` (impacte directement les montants) | B-091 |
| 3.3 | Tests `LicenseUtil` (valide / expirée / falsifiée) | B-092 |
| 3.4 | Tests `AnomalyEngine` | B-093 |
| 3.5 | Tests de repository sur base Room en mémoire (vente + stock + recette + dette) | B-094 |
| 3.6 | Supprimer les tests générés vides | B-096 |

**Critère de sortie** : couverture significative sur `utils/` et `sms/` ;
`REPORTS/rapport_couverture_<date>.md`.

---

## Jalon 4 — Hilt et assainissement

**Pourquoi ici** : Hilt est un prérequis mécanique au découpage du repository
et des ViewModels. Le faire avant J3 serait un refactor à l'aveugle.

| Ordre | Tâche | Réf. |
|---|---|---|
| 4.1 | Mise en place de Hilt (plugin + application + activity) | B-030 |
| 4.2 | `DatabaseModule` (base + 20 DAO) | B-031 |
| 4.3 | `RepositoryModule` avec `@ApplicationContext` | B-032 |
| 4.4 | `@HiltViewModel` ; suppression de la factory manuelle (corrige BUG-007) | B-033 |
| 4.5 | `@HiltWorker` pour les deux workers | B-034 |
| 4.6 | `EntryPointAccessors` pour `SmsReceiver` ; corriger son scope | B-035, B-038 |
| 4.7 | Fusionner les `NotificationHelper` | B-036 |
| 4.8 | Supprimer les accès base depuis `ui/` | B-037 |
| 4.9 | Sortir permissions et WorkManager de `MainActivity` | B-039 |
| 4.10 | Supprimer le code mort identifié | B-040, B-041 |

**Critère de sortie** : plus aucune instanciation manuelle de dépendance ;
tests de J3 toujours verts ; `ARCHITECTURE.md` mis à jour.

---

## Jalon 5 — Découpage architectural

Refactor progressif, **un domaine à la fois**, chacun livré et validé séparément.

| Ordre | Tâche | Réf. |
|---|---|---|
| 5.1 | Extraire `SalesRepository` de `MainRepository` | B-050 |
| 5.2 | Extraire `StockRepository` | B-050 |
| 5.3 | Extraire `CustomerRepository` / `SupplierRepository` | B-050 |
| 5.4 | Extraire `CashRepository` (sessions, clôtures, audits) / `SettingsRepository` | B-050 |
| 5.5 | Sortir la génération PDF/ticket du repository | B-051 |
| 5.6 | Sortir les `Intent` Android vers `platform/` | B-052 |
| 5.7 | `UiState` + ViewModel dédié pour `NewSaleScreen` (écran pilote) | B-053, B-054 |
| 5.8 | Étendre le motif aux écrans restants, un par un | B-054 |
| 5.9 | Statuts en `enum class` | B-055 |
| 5.10 | Paginer l'historique des ventes | B-056 |
| 5.11 | Type d'erreur scellé `AppError` | B-059 |

**Critère de sortie** : aucun fichier > 400 lignes ; `ARCHITECTURE.md` reflète
la nouvelle structure.

---

## Jalon 6 — Fonctionnalités manquantes

| Ordre | Tâche | Réf. |
|---|---|---|
| 6.1 | Écran de gestion du personnel (débloque réellement les rôles) | B-070 |
| 6.2 | Pilotage complet des sessions de caisse | B-071 |
| 6.3 | Refonte du parcours de permissions | B-078 |
| 6.4 | Externalisation des textes + `values-en/` | B-073, B-074 |
| 6.5 | Snackbars cohérents | B-076 |
| 6.6 | Alertes d'anomalie visibles dans l'UI | B-072 |
| 6.7 | Décision « sync cloud » : assumer ou spécifier | B-075 |
| 6.8 | Étude du passage à un type monétaire exact | B-077 |

**❓ Décision produit attendue (6.7)** : la « synchronisation cloud » est
aujourd'hui un simple partage de fichier. Faut-il (a) l'assumer et le renommer
honnêtement, (b) intégrer un stockage tiers (Drive/Dropbox), ou (c) construire
un backend ? **Le (c) changerait la nature du projet.**

---

## Jalon 7 — Industrialisation

| Ordre | Tâche | Réf. |
|---|---|---|
| 7.1 | ktlint ou detekt | B-097 |
| 7.2 | CI GitHub Actions (build + test + lint sur PR) | B-098 |
| 7.3 | Couverture JaCoCo publiée | B-099 |
| 7.4 | Tests Compose des parcours critiques | B-095 |
| 7.5 | Configuration de signature et de release |  |

---

## Séquencement proposé

| Jalon | Contenu | Charge indicative |
|---|---|---|
| J0 | Build vérifiable | 1 session |
| J1 | Intégrité des données | 2–3 sessions |
| J2 | Sécurité | 2 sessions |
| J3 | Filet de tests | 2 sessions |
| J4 | Hilt + assainissement | 2–3 sessions |
| J5 | Découpage | 4–6 sessions (incrémental) |
| J6 | Fonctionnalités | 3–4 sessions |
| J7 | Industrialisation | 1–2 sessions |

*Une « session » = une tâche `CURRENT_TASK.md` menée à terme, vérifiée et documentée.*

---

## Ce que je propose de faire en premier

**Jalon 0, tâche 0.1 → 0.7** (chantier « build vérifiable »), car aucune autre
correction ne peut être **prouvée** tant que le projet ne compile pas de façon
reproductible.

**Alternative** si vous jugez le risque de données prioritaire : commencer
directement par **J1.1 + J1.2** (export des schémas + harnais de test de
migration), qui sont analysables statiquement même sans build complet.

👉 **Merci de valider (a) l'ordre des jalons, (b) le point de départ, et de
répondre aux deux décisions produit (1.4 et 6.7).**

*Document créé le 2026-07-28 — non validé.*
