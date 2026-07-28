# 📐 RÈGLES DE CODE (non négociables)

Ces règles s'appliquent à **tout code nouveau**. Le code existant qui les viole
est recensé dans `BACKLOG.md` et corrigé progressivement, **jamais en masse au
milieu d'une autre tâche**.

---

## 1. MVVM obligatoire

```
Composable  →  ViewModel  →  Repository  →  DAO  →  Room
```

- Un Composable **ne connaît que son ViewModel**.
- Un Composable ne fait **jamais** : accès base, I/O fichier, `Intent`,
  `Context`-plomberie, calcul métier.
- Le ViewModel expose un état **immuable** (`StateFlow`) et des fonctions
  d'intention (`onValidateSale()`, `onQuantityChanged()`).
- ❌ Interdit : `AppDatabase.getDatabase(context)` dans `ui/`.
  (5 violations existantes : `SplashScreen`, `ClosureScreen`, `SettingsScreen`, …)

## 2. Repository obligatoire

- Toute donnée transite par un repository. Aucun DAO appelé depuis un ViewModel.
  ❌ Violations existantes : `MainViewModel` appelle `db.priceHistoryDao()`,
  `db.stockMovementDao()`, `db.staffDao()`, `db.customerDao()` directement.
- Un repository expose des `Flow` pour la lecture et des `suspend fun` pour l'écriture.
- Un repository **ne lance pas de coroutine** : il est appelé depuis un scope existant.
- Les nouveaux repositories sont **par domaine** (`SalesRepository`,
  `StockRepository`, `CustomerRepository`…), pas un nouveau god-object.

## 3. Pas de logique métier dans les Activities

- `MainActivity` ne doit contenir que : `setContent`, thème, edge-to-edge.
- Les permissions et la planification WorkManager migrent vers
  `CaisseApplication` / une couche dédiée.
- Aucune règle de calcul, aucun accès base dans une Activity.

## 4. Room uniquement via DAO

- Pas de `rawQuery`, pas de SQL construit par concaténation de chaînes.
  ❌ Violation existante : `PRAGMA rekey = '$passphrase'` (BUG-006).
- Toute requête est une méthode `@Query` annotée dans un `@Dao`.
- Toute opération multi-tables passe par `db.withTransaction { }`.
- Tout changement de schéma → migration **+ test de migration** (voir `DATABASE.md` §4).

## 5. Coroutines

- Concurrence structurée uniquement : `viewModelScope`, `lifecycleScope`,
  `CoroutineWorker`, `withContext`.
- ❌ Interdit : `GlobalScope`, `CoroutineScope(...)` créé à la volée sans annulation.
  Violation existante : `SmsReceiver` (BUG-009).
- Le choix du dispatcher appartient à la couche basse (`withContext(Dispatchers.IO)`
  dans le repository), **pas** à l'appelant.
- Les fonctions `suspend` sont *main-safe*.
- ❌ Interdit : `catch (e: Exception) { }` vide. Toujours logger **et** remonter
  un état d'erreur exploitable. Ne jamais avaler `CancellationException`.

## 6. Flow

- Lecture réactive = `Flow` du DAO → transformé dans le repository →
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Côté Compose : `collectAsStateWithLifecycle()` (préférer à `collectAsState()`).
- Pas de `Flow` mutable exposé : `_x: MutableStateFlow` privé, `x: StateFlow` public.
- Pas de `.first()` dans un chemin réactif ; réservé aux one-shot.

## 7. Hilt

Cible d'architecture (voir `ROADMAP.md`, jalon 2) :

- `@HiltAndroidApp` sur `CaisseApplication`.
- `@AndroidEntryPoint` sur `MainActivity`.
- `@HiltViewModel` + `@Inject constructor` pour chaque ViewModel.
- `@Module @InstallIn(SingletonComponent::class)` pour `AppDatabase`, DAO,
  `PreferencesManager`, `EscPosPrinter`, repositories.
- `@HiltWorker` + `HiltWorkerFactory` pour `BackupWorker` et `SubscriptionWorker`.
- `EntryPointAccessors` pour `SmsReceiver` (BroadcastReceiver).
- ❌ Après ce jalon : plus aucun `new`/constructeur manuel de dépendance,
  plus aucune `ViewModelProvider.Factory` écrite à la main.

## 8. Material 3

- `androidx.compose.material3` uniquement. ❌ Pas de `material` (M2), pas de Views.
- Couleurs via `MaterialTheme.colorScheme`, jamais de `Color(0xFF...)` en dur
  dans un écran (les couleurs vivent dans `ui/theme/Color.kt`).
- Typographie via `MaterialTheme.typography`.
- Espacements en `dp` multiples de 4.
- Cibles tactiles ≥ 48 dp (usage terrain, souvent à une main).

## 9. Nommage cohérent

| Élément | Convention | Exemple |
|---|---|---|
| Entité Room | `XxxEntity` | `VenteEntity` |
| DAO | `XxxDao` | `VenteDao` |
| Repository | `XxxRepository` | `SalesRepository` |
| ViewModel | `XxxViewModel` | `NewSaleViewModel` |
| État d'écran | `XxxUiState` | `NewSaleUiState` |
| Écran Compose | `XxxScreen` | `NewSaleScreen` |
| Composant réutilisable | nom métier | `NumericKeypad` |
| Constante | `UPPER_SNAKE_CASE` | `PBKDF2_ITERATIONS` |
| Fonction | `lowerCamelCase`, verbe | `addVenteWithItems` |
| Booléen | `is…`, `has…`, `should…` | `isSetupComplete` |

⚠️ Le projet mélange français et anglais (`VenteEntity` / `StockEntity`,
`ventes` / `customers`). **Règle figée** : on **conserve** les noms existants
(renommer casserait la base). Pour tout **nouveau** symbole : **anglais** pour
le code technique, français pour le texte affiché.

## 10. Pas de duplication

- Avant de créer une classe/fonction : `grep -rn "<nom>" app/src`.
- ❌ Violation existante : deux `NotificationHelper` (`notification/` et `utils/`).
- Toute constante utilisée deux fois devient une constante nommée
  (statuts `"CONFIRMED"`, `"PENDING"`, rôles `"MANAGER"`, `"STAFF"` → **à passer en
  `enum class` ou objets de constantes**).
- Tout bloc de plus de ~15 lignes copié-collé est factorisé.

## 11. Code documenté lorsque nécessaire

- KDoc obligatoire pour : fonctions publiques de repository, algorithmes non
  évidents (`SmsParser`, `FeeCalculator`, `SecurityUtil`, `AnomalyEngine`),
  toute migration Room.
- KDoc **interdit** quand il paraphrase le nom (`/** Retourne le nom */`).
- Les commentaires expliquent le **pourquoi**, jamais le **quoi**.
- Langue des commentaires : **français** (cohérent avec l'existant).
- Tout `TODO` doit référencer une ligne du `BACKLOG.md` : `// TODO(BACKLOG-42): …`

## 12. Règles transverses

- **Aucun secret en dur** (clé, sel, numéro de téléphone privé). Voir `SECURITY.md`.
- **Aucun texte utilisateur en dur** dans du code neuf → `strings.xml`.
- **Aucun `!!`** sauf justification écrite en commentaire.
- **Aucun `println`** ; `android.util.Log` avec un TAG constant par classe.
- Les montants sont des `Double` (existant) — ne jamais comparer par `==` ;
  utiliser une tolérance. *(Migration vers `BigDecimal`/`Long` centimes : backlog P4.)*
- Fichier > 400 lignes = signal de découpage à consigner dans le backlog.


---

## 13. Définition de « terminé » — validation obligatoire

> **Aucun correctif ne peut être considéré comme terminé tant que :**
> - **la compilation réelle a réussi ;**
> - **les tests automatisés sont passés ;**
> - **aucune régression n'a été détectée.**

Cette règle est **non négociable** et prévaut sur toute impression de complétude.

### 13.1 Écrire du code n'est pas corriger un bug

Un correctif non exécuté est une **hypothèse**, aussi solide soit le
raisonnement qui l'a produit. Le vocabulaire doit refléter cette différence :

| Formulation interdite ❌ | Formulation exigée ✅ |
|---|---|
| « BUG-018 est corrigé » | « BUG-018 : corrigé par inspection statique, en attente de compilation réelle » |
| « ça compile » | « non compilé — l'environnement ne le permet pas » |
| « les tests passent » | « 26 tests écrits, non exécutés » |
| « c'est terminé » | « livré, en attente de validation » |

**Précédent qui fonde cette règle** : `BackupManager` contenait quatre défauts
de sécurité *et* une erreur de type (`Result<Result<Unit>>`, BUG-023) qui le
rendait non compilable. Il avait pourtant été livré comme fonctionnel à l'issue
d'un audit de sécurité. **Un compilateur l'aurait détecté en une seconde ;
aucune relecture ne l'avait vu.**

### 13.2 Statuts autorisés

Voir l'échelle de `BUGS.md`. En résumé :

- `CORRIGÉ (INSPECTION)` — correctif écrit, **non exécuté**. État transitoire,
  jamais terminal. C'est une **dette de vérification**.
- `CORRIGÉ (VALIDÉ)` — compilation ✅ + tests ✅ + aucune régression ✅.
  **Seul statut autorisant la clôture.**

### 13.3 Chaîne de validation

```bash
make verify      # environnement Docker opérationnel
make validate    # compilation → analyses statiques → tests unitaires
make instrumented   # si le diff touche SQLCipher, Keystore, migrations ou UI
```

Un bug ne passe en `CORRIGÉ (VALIDÉ)` **qu'après** production des rapports
correspondants dans `.ai/REPORTS/`.

### 13.4 En cas d'échec

1. **Arrêter** l'intégration ;
2. **analyser** l'erreur — sans supposer qu'elle est bénigne ;
3. **corriger** ;
4. **relancer la chaîne complète**, pas seulement l'étape échouée ;
5. ne déclarer résolu qu'après réussite intégrale.

### 13.5 Quand l'environnement ne permet pas de valider

Si la compilation est impossible (absence de JDK, de SDK ou de Docker) :

- **le dire explicitement**, dans `PROGRESS.md` **et** dans la réponse au
  responsable ;
- marquer les bugs concernés `CORRIGÉ (INSPECTION)` ;
- fournir les **commandes exactes** permettant à un tiers de valider ;
- **ne jamais** présenter une inspection comme une preuve d'exécution.

### 13.6 Double validation des composants critiques

Pour tout composant critique — cryptographie, migrations de base, calculs
financiers, parsing SMS — **deux validations complémentaires sont exigées** :

| # | Validation | Ce qu'elle prouve | Ce qu'elle ne prouve pas |
|---|---|---|---|
| 1 | **Algorithmique indépendante** — implémentation de référence dans un autre langage (Python, table de vecteurs, oracle externe) | la **logique** et le **format** sont corrects | que le code Kotlin livré est correct |
| 2 | **Réelle en Kotlin** — tests exécutés dans Docker sur le code du projet | le **code livré** fonctionne | que la logique est juste, si les deux partagent le même malentendu |

Les deux sont **complémentaires, jamais interchangeables**. Une divergence entre
elles est un signal fort : l'une des deux implémentations est fausse.

**Précédent** : `tools/verification/verify_backup_format.py` réimplémente le
format d'archive v2 en Python et a **reproduit BUG-018** (`CT||TAG||TAG` →
`InvalidTag`) avant de valider le correctif. Cette validation a été possible
**sans JDK** — mais elle ne dispense pas d'exécuter `BackupManagerTest.kt`.

**Composants soumis à la double validation** :

| Composant | Validation indépendante | Validation Kotlin |
|---|---|---|
| `BackupManager` (crypto) | ✅ `verify_backup_format.py` — 16/16 | ⏳ 26 tests écrits, non exécutés |
| `SmsParser` | ⬜ jeu de SMS de référence à constituer | ⬜ B-090 |
| `FeeCalculator` | ⬜ barèmes officiels MTN/Orange | ⬜ B-091 |
| `SecurityUtil` (PBKDF2) | ⬜ vecteurs RFC 6070 | ⚠️ partiel |
| Migrations Room | ⬜ schémas JSON de référence (B-009) | ⬜ B-012 |
| `LicenseUtil` (HMAC) | ⬜ vecteurs HMAC-SHA256 | ⬜ B-092 |

Les harnais indépendants vivent dans `tools/verification/` et sont **conservés**
après usage : ils servent d'oracle anti-régression lors des évolutions.
