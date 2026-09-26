# 🎯 TÂCHE EN COURS

**Tâche :** aucune implémentation en cours. J6.3 est clôturé techniquement. Le prochain jalon fonctionnel est J6.5 (snackbars), non commencé.

## État réel

| Élément | Valeur |
|---|---|
| HEAD | `52b4090917c02df6d83bde2f4beeab8657e361e2` |
| Commit | `feat(j6): implement J6.3 permission handling` |
| Parent | `082f2e0f37988933fe545861a5ac9e40130bc2a8` |
| J6.1A / J6.4 | TERMINÉ TECHNIQUEMENT (`82f05be`) |
| J6.2 | TERMINÉ TECHNIQUEMENT (`8658ab5`) |
| J6.3 | TERMINÉ TECHNIQUEMENT (`52b4090`) |
| Working Tree après `52b4090` | propre, avant cette modification documentaire |
| Sync documentaire | en working tree, **pas encore committée** |

## J6.3 — Permissions / BUG-010

Livré dans le commit `52b4090` (16 fichiers techniques, aucun `.ai/`) :
- plus de demande groupée au démarrage ;
- demandes au point d'usage + rationale + refus simple / définitif + Paramètres + relecture au resume ;
- `READ_SMS` : `SecurityException` capturée, résultat exploitable, caisse non bloquée ;
- caméra dégradée (saisie manuelle) ; Bluetooth gardé avant `bondedDevices` / `connect` ;
- 141 tests JVM ; `compileDebugKotlin` OK ; `assembleDebug` **non exécuté** ; i18n 699/699.

## Encore ouvert dans le jalon 6

Snackbars (6.5 / B-076) — **NEXT**. Alertes d'anomalie (6.6), sauvegarde distante (6.7), type monétaire (6.8).

Résidus J6.3 (non transformés en tâches terminées) :
- `BLUETOOTH_SCAN` déclaré mais inutilisé ;
- notifications non redemandées aux utilisateurs déjà onboardés ;
- bannière `READ_SMS` après retour Paramètres ;
- tests permission uniquement JVM ;
- B-039 / WorkManager hors scope.

## Historique

Les jalons J5.8.2 à J5.8.6, J6.1A et J6.2 restent consignés dans `ROADMAP.md`, `DEVLOG.md` et `TRACEABILITY.md`. Ils ne sont pas réécrits ici.

---
*Mis à jour le 2026-09-26 (clôture documentaire J6.3, non committée). Précédente fiche : 2026-09-26, post-J6.2.*
