# 🎯 TÂCHE EN COURS

**Tâche :**
**Jalon D — Environnement Docker (Phase 6)** : livré, **en attente de première
exécution par le responsable**.

⛔ **BLOCAGE — action requise de votre part.**
L'environnement de l'agent n'a **ni Docker, ni JDK, ni SDK Android**
(`docker: command not found`, `java: command not found`). L'environnement est
écrit et vérifié syntaxiquement, mais **jamais exécuté**.

```bash
cd MobileCaisse
make image     # ~5-10 min la première fois
make verify    # doit conclure « Environnement validé »
```

Puis me transmettre la sortie.

**Objectif :**
Disposer d'un environnement de build reproductible et **prouvé fonctionnel**,
préalable obligatoire à toute modification de code (Phase 6).

**Contraintes :**
- Aucune modification de code de production tant que `make verify` n'a pas réussi.
- ⚠️ `make validate` échouera **probablement** à la compilation à cause de
  **BUG-003** (`androidx.appcompat` utilisé mais non déclaré). C'est **attendu**
  et utile : première preuve objective d'un défaut jusqu'ici établi par lecture
  seule. Sa correction est **B-003**, première tâche de J0.
- Ne pas régresser les 5 correctifs de sécurité vérifiés (voir `SECURITY.md` §0).

---

## File d'attente validée (après déblocage de l'environnement)

| # | Tâche | Réf. | Décision |
|---|---|---|---|
| 1 | Réparer `BackupManager` : AES-GCM, chiffrement de la base, `Instant`, version | B-120→B-123 | ⏳ arbitrage en attente |
| 2 | Tests unitaires `BackupManager` | B-124 | |
| 3 | Brancher `BackupManager` sur les chemins A, B, C | B-101 | demandé |
| 4 | Corriger `staff.pinSalt` (la **migration** a tort) | B-125 | verdict rendu |
| 5 | **J0** — build propre, nettoyage, `ComponentActivity` + Material3, B-110 | B-001→B-008, B-100 | D3, B-003, B-110 |
| 6 | **J1.1 + J1.2** — `exportSchema` + harnais `MigrationTestHelper` | B-009, B-012 | D3 |
| 7 | **J1.3** — refonte complète de la chaîne de migrations | B-010 | J1.3 |

### Arbitrages toujours en attente
1. **`BackupManager` cassé** — je répare (B-120→B-124) avant de brancher, ou je
   branche d'abord ? *(recommandation : réparer d'abord — brancher un module
   cassé produirait des sauvegardes irrécupérables)*
2. **BUG-019** — chiffrer la base avec le mot de passe (archive protégée en
   propre, mémoire ×2 pendant l'opération), ou s'en tenir à SQLCipher et
   corriger seulement le libellé ?

---

*Mis à jour le 2026-07-28 (rév. 4 — Phase 6).*
