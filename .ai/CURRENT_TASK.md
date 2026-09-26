# 🎯 TÂCHE EN COURS

**Tâche :** synchronisation documentaire J6.5. Aucune implémentation en cours. J6.5 est clôturé techniquement. **J6.6 / LOT 4 n'est pas commencé.**

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `498b9ba90509122b7e98d59c96879b0b933889bf` |
| Commit | `feat(j6): implement J6.5 snackbar and feedback handling` |
| Parent | `d0e608e6dfc6c4e934ae44601ce5f4e18bb81057` |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| J6.5 | TERMINÉ TECHNIQUEMENT (`498b9ba`) — post-commit **VALIDÉ** |
| Working Tree après `498b9ba` | propre, avant cette modification documentaire |
| Sync documentaire | en working tree, **pas encore committée** |

## J6.5 — Snackbars / B-076 (terminé)

Livré dans le commit `498b9ba` (25 fichiers techniques, +264 / −38, aucun `.ai/`) :
- LOT 1 (19) : Customer, Debt, Expense, Reports, Staff, NewSale ;
- LOT 2 (2) : Home, Closure (`SessionUiState.Error`) ;
- LOT 3 (4) : SalesList, Stock, SmsError, Support (`uiError` → Snackbar → `clearError()`) ;
- vente rapide Stock : `Job.join()` puis succès seulement si `uiError.value == null` ;
- 147/147 tests JVM ; `compileDebugKotlin` / KSP / **`assembleDebug` OK** ; i18n 700/700.

Phase actuelle = **documentation uniquement**. Pas de LOT 4. Pas de J6.6.

## Encore ouvert dans le jalon 6

Alertes d'anomalie (6.6 / B-072) — **NEXT / NOT STARTED**. Sauvegarde distante (6.7), type monétaire (6.8).

Dettes J6.5 conservées (hors scope, non résolues) :
- SaleDetail / Settings-Splash : `uiError` non collecté (navigation immédiate) ;
- `convertErrorToOrphan` inutilisé ;
- `PrintSettings.testPrint` silencieux ;
- overlay dialog Home.

Résidus J6.3 (inchangés) :
- `BLUETOOTH_SCAN` déclaré mais inutilisé ;
- notifications non redemandées aux utilisateurs déjà onboardés ;
- bannière `READ_SMS` après retour Paramètres ;
- tests permission uniquement JVM ;
- B-039 / WorkManager hors scope.

## Historique

Les jalons J5.8.2 à J5.8.6, J6.1A, J6.2 et J6.3 restent consignés dans `ROADMAP.md`, `DEVLOG.md` et `TRACEABILITY.md`. Ils ne sont pas réécrits ici.

---
*Mis à jour le 2026-09-26 (clôture documentaire J6.5, non committée). Précédente fiche : 2026-09-26, post-J6.3.*
