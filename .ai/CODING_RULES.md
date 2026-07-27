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
