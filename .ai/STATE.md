# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit analysé** : a3a5bfd866af8dc1e5181084920f81f1a8381b1d
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-05 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-05 (65/65 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - MainRepository (Orchestration validée sur JVM - Niveau A)
    - Restauration physique (Validée)
- **Dette technique** :
    - Repository "God Object" (MainRepository > 1300 l.)
    - Pas d'injection de dépendances (Hilt manquant)
    - Intégration Room/SQLCipher réelle non testée sur Runtime (B-094B en attente)

## 📋 Progression
- **Dernière tâche terminée** : Jalon 3 - Filet de tests (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire post-J3.
- **Prochaine tâche prévue** : Jalon 4.1 - Mise en place de Hilt.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - ADR-002 : Restauration physique sécurisée.
    - B-094 split : Séparation de l'orchestration (JVM) et de l'intégration (Android Runtime).
- **Risques connus** : 
    - Divergence possible entre les Fake DAOs et le comportement réel de Room/SQLCipher (sera levé par B-094B).

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-05
- **Agent** : AI-DOS Independent Auditor
