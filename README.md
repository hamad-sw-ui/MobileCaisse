# Mobile Caisse

Application Android de caisse enregistreuse pour commerçants, avec
**réconciliation automatique des paiements Mobile Money par lecture des SMS**
(MTN MoMo, Orange Money — Cameroun).

100 % hors ligne · Kotlin · Jetpack Compose · Room chiffré par SQLCipher.

---

## Démarrage rapide

Le projet se construit **dans Docker** — aucun JDK ni SDK Android n'est requis
sur votre machine.

```bash
make image      # construire l'image (~5-10 min, une seule fois)
make verify     # valider l'environnement
make build      # compiler
make test       # tests unitaires
```

`make help` liste toutes les cibles. Documentation complète :
[`.ai/DEV_ENVIRONMENT.md`](.ai/DEV_ENVIRONMENT.md).

> Sans Docker : Android Studio, JDK **21** obligatoire
> (`gradle/gradle-daemon-jvm.properties` impose `toolchainVersion=21`).

## Fonctionnalités

| Domaine | Contenu |
|---|---|
| **Ventes** | Panier, prix VIP / détail / vrac, remise, taxe, frais MoMo, paiement mixte, facture séquentielle, retours et avoirs |
| **Stock** | Code-barres (ML Kit), vente en vrac, recettes composées, seuils d'alerte, mouvements, historique de prix |
| **Mobile Money** | Lecture des SMS, rapprochement automatique, déduplication, file des SMS non reconnus |
| **Clients** | Dettes, remboursements, statut VIP, relances SMS, relevés PDF |
| **Caisse** | Sessions, clôture de journée, inventaire, journal d'actions |
| **Impression** | Tickets ESC/POS via Bluetooth |
| **Sauvegarde** | Locale quotidienne, export chiffré par mot de passe, restauration |

## Architecture

```
UI (Compose) → MainViewModel → MainRepository → DAO → Room (SQLCipher)
                                    ↑
              BackupWorker · SubscriptionWorker · SmsReceiver
```

Module unique `:app`. Détail : [`.ai/ARCHITECTURE.md`](.ai/ARCHITECTURE.md).

| | |
|---|---|
| minSdk / targetSdk | 24 / 35 |
| Kotlin / AGP / Gradle | 2.1.0 / 8.13.2 / 9.5.0 |
| Base | Room 2.7.0-alpha12 + SQLCipher, schéma v28 |

## Contribuer

Ce dépôt suit un cadre de travail documenté dans **[`.ai/`](.ai/)**, qui fait
office de source de vérité.

Avant toute contribution :

1. lire [`.ai/README.md`](.ai/README.md) et [`.ai/CODING_RULES.md`](.ai/CODING_RULES.md) ;
2. consulter [`.ai/CURRENT_TASK.md`](.ai/CURRENT_TASK.md) ;
3. dérouler [`.ai/CHECKLISTS/avant_commit.md`](.ai/CHECKLISTS/avant_commit.md).

Règles structurantes : MVVM · Repository · Room via DAO · coroutines et Flow ·
Material 3 · **aucun correctif n'est terminé sans compilation réelle et tests
passés** (§13) · **analyse d'impact avant toute modification** (§14).

## État du projet

🚧 **En développement — aucun déploiement en production.**

Le projet est fonctionnellement riche (~85 % du métier écrit) mais comporte des
défauts connus, recensés dans [`.ai/BUGS.md`](.ai/BUGS.md) et planifiés dans
[`.ai/ROADMAP.md`](.ai/ROADMAP.md).

⚠️ Points saillants : migrations Room divergentes des entités (BUG-001),
couverture de tests faible.

## Licence

Propriétaire — tous droits réservés.
