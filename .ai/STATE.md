# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit analysé** : 056ae4b1a2196740c3912708130b92331e58a8c5
- **Version du Framework** : 2.1.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2024-05-22 (SUCCESS)
- **Dernière exécution des tests** : 2024-05-22 (65/65 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - MainRepository (Orchestration validée sur JVM - Niveau A)
    - Restauration physique (Validée)
- **Dette technique** :
    - Repository "God Object" (MainRepository > 1000 l.)
    - Pas d'injection de dépendances (Hilt manquant)
    - Intégration Room/SQLCipher réelle non testée sur Runtime (B-094B en attente)

## 📋 Progression
- **Dernière tâche terminée** : Jalon 3 - Filet de tests (CLÔTURÉ).
- **Tâche en cours** : Préparation Jalon 4 - Hilt + Assainissement.
- **Prochaine tâche prévue** : Jalon 4.1 - Mise en place de Hilt.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (INSPECTION)** : BUG-003, BUG-011, BUG-014, BUG-016, BUG-017, BUG-022, BUG-024, BUG-025.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-005, BUG-006, BUG-015, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001 (Verrou classes.jar).

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - ADR-002 : Restauration physique sécurisée.
    - B-094 split : Séparation de l'orchestration (JVM) et de l'intégration (Android Runtime).
- **Risques connus** : 
    - Divergence possible entre les Fake DAOs et le comportement réel de Room/SQLCipher (sera levé par B-094B).

## 🕒 Dernière Mise à jour
- **Date** : 2024-05-22
- **Agent** : Gemini (AI-DOS Mode Autonome)
