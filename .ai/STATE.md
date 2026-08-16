# 🧠 ÉTAT DU PROJET (STATE)

Ce document est la mémoire officielle du projet. Il est mis à jour à la fin de chaque cycle de développement.

## 📌 Identification
- **Projet** : MobileCaisse
- **Branche actuelle** : arena/019fa5ec-mobilecaisse
- **Dernier commit fonctionnel** : 563eee301a44eacdaed537d7b0e7ae5794991e5d
- **HEAD actuel** : 563eee301a44eacdaed537d7b0e7ae5794991e5d
- **Version du Framework** : 3.0.0 (AI-DOS + Evidence Audit)

## 🛠️ État Technique
- **Dernière compilation** : 2026-08-13 (SUCCESS)
- **Dernière exécution des tests** : 2026-08-13 (75/75 SUCCESS)
- **Couverture actuelle** : 
    - BackupManager (100% logique)
    - SmsParser (100% logique métier)
    - FeeCalculator (100% logique calculs MoMo)
    - LicenseUtil (100% logique HMAC)
    - AnomalyEngine (100% logique détection fraude)
    - MainRepository (Délégation Stock/Sales)
    - SalesRepository (Domaine Sales complet + Clients + Sessions)
    - InventoryRepository (Domaine Inventory complet + Fournisseurs + Audits + Recettes)
- **Dette technique** :
    - `MainRepository` (645 lignes) contient encore des méthodes "proxy".
    - `MainViewModel` (717 lignes) centralise encore la logique de tous les écrans.
    - Logique PDF/Ticket dans `SalesRepository` (dépendance Android dans data).

## 📋 Progression
- **Dernière tâche terminée** : B-050c - Extraction Inventory (CLÔTURÉ).
- **Tâche en cours** : Synchronisation documentaire.
- **Prochaine tâche prévue** : B-051 - Extraction Génération PDF/Ticket.

## 🐞 Bugs & Défauts
- **Bugs Ouverts (🔴/🟠/🟡)** : BUG-004, BUG-007, BUG-008, BUG-009, BUG-010, BUG-012, BUG-013.
- **Bugs Corrigés (VALIDÉ)** : BUG-001, BUG-002, BUG-003, BUG-005, BUG-006, BUG-011, BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-090, BUG-091, BUG-092, BUG-093, B-094A, BUG-ENV-001.

## 🏛️ Décisions & Risques
- **Décisions d'architecture** : 
    - ADR-002 : Restauration physique sécurisée.
    - fragmentation de `MainRepository` par domaines fonctionnels (Sales, Inventory).
    - Regroupement des domaines satellites (Clients/Sessions -> Sales, Fournisseurs/Audits -> Inventory) par cohérence technique.
- **Risques connus** : 
    - Risque de circularité lors des extractions complexes (sécurisé par le plan J5).
    - Intégrité transactionnelle (sécurisée par injection `AppDatabase`).

## 🕒 Dernière Mise à jour
- **Date** : 2026-08-13
- **Agent** : AI-DOS Independent Auditor
