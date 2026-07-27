# ✅ CHECKLIST — AVANT COMMIT

À dérouler **intégralement** avant tout `git commit`.
Une case non cochable = on ne committe pas, ou on documente pourquoi.

---

## 1. ☐ Compile

```bash
./gradlew assembleDebug
```

- [ ] La compilation réussit
- [ ] Le module `:app` est bien construit
- [ ] KSP a généré les classes Room sans erreur

⚠️ **Environnement de l'agent** : ni JDK ni SDK Android disponibles.
Si la compilation n'a pas pu être exécutée, l'écrire **explicitement** dans
`PROGRESS.md` (« non compilé — vérification statique uniquement »).
**Ne jamais affirmer qu'un code compile sans l'avoir prouvé.**

## 2. ☐ Aucun warning critique

```bash
./gradlew assembleDebug --warning-mode all
./gradlew lint
```

- [ ] Aucun warning Kotlin nouveau introduit par le diff
- [ ] Aucune erreur Android Lint de sévérité `Error`
- [ ] Aucun avertissement de dépréciation sur une API de sécurité ou de base
- [ ] Aucun warning Room (schéma, index de clé étrangère manquant, requête ambiguë)

## 3. ☐ Tests réussis

```bash
./gradlew test
./gradlew connectedAndroidTest   # si un appareil est disponible
```

- [ ] Tous les tests unitaires passent
- [ ] Les tests de migration passent (si le schéma a changé)
- [ ] Un test de non-régression a été ajouté pour tout bug corrigé
- [ ] Aucun test désactivé (`@Ignore`) sans justification écrite

## 4. ☐ Pas de fuite mémoire

Vérification par revue (pas d'outil automatisé dans le projet à ce jour) :

- [ ] Aucun `Context` d'Activity stocké dans un singleton, un repository ou un ViewModel
- [ ] Aucune référence à une Vue, une Activity ou un `NavController` dans un ViewModel
- [ ] Toute coroutine est lancée dans un scope à durée de vie maîtrisée
      (`viewModelScope`, `lifecycleScope`, `CoroutineWorker`)
- [ ] Aucun `CoroutineScope(...)` créé à la volée sans annulation
- [ ] `Cursor`, `BluetoothSocket`, `InputStream`/`OutputStream` fermés (`use { }`)
- [ ] Les collecteurs de `Flow` sont liés au cycle de vie
      (`collectAsStateWithLifecycle`)
- [ ] Aucun `remember` capturant une valeur non stable de longue durée

## 5. ☐ Pas de code mort

- [ ] Aucune fonction, classe ou import ajouté puis inutilisé
- [ ] Aucun code commenté laissé en place (Git est là pour l'historique)
- [ ] Aucun `TODO` sans référence au backlog (`// TODO(B-042): …`)
- [ ] Aucun fichier temporaire, de test manuel ou de brouillon
- [ ] Aucun `println` ni log de débogage oublié

```bash
git diff --cached | grep -nE "println|Log\.(d|v)\(|TODO|FIXME|XXX"
```

---

## 6. ☐ Contrôles transverses

- [ ] Aucun secret, clé, mot de passe ou numéro privé dans le diff
- [ ] Aucun fichier d'IDE (`.idea/`, `.kotlin/`) ni artefact de build indexé
- [ ] Aucun texte utilisateur codé en dur dans du code neuf
- [ ] Le diff est minimal (pas de reformatage parasite)

```bash
git status
git diff --cached --stat
```

## 7. ☐ Documentation

- [ ] `.ai/PROGRESS.md` — entrée du jour renseignée (6 rubriques)
- [ ] `.ai/BACKLOG.md` — tâches cochées `☑`, tableau de synthèse à jour
- [ ] `.ai/BUGS.md` — mis à jour si un bug est corrigé ou découvert
- [ ] `.ai/ARCHITECTURE.md` / `DATABASE.md` / `DEPENDENCIES.md` — mis à jour si
      la structure, le schéma ou les dépendances ont changé

## 8. ☐ Message de commit

Format : `<type>(<portée>): <description à l'impératif>`

```
fix(room): aligner la migration 24_25 sur SessionEntity
feat(staff): ajouter l'écran de gestion du personnel
docs(ai): mettre à jour ARCHITECTURE après l'introduction de Hilt
refactor(repository): extraire SalesRepository de MainRepository
test(sms): couvrir SmsParser sur les formats MTN et Orange
chore(build): déplacer sqlcipher vers le version catalog
```

- [ ] Le type est correct (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`)
- [ ] La description tient en une ligne et dit **quoi**, pas comment
- [ ] Le corps explique le **pourquoi** si ce n'est pas évident
- [ ] Le bug ou la tâche est référencé (`BUG-001`, `B-010`)

---

## 9. ☐ Branche

- [ ] Je suis bien sur `arena/019fa5ec-mobilecaisse`

```bash
git branch --show-current
```
