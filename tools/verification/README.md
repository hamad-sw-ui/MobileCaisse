# tools/verification/

Harnais de validation **indépendants de la JVM**.

## `verify_backup_format.py`

Réimplémente en Python le format d'archive v2 de `BackupManager` (PBKDF2 100k,
AES-GCM avec AAD, ZIP à 3 entrées) et vérifie 16 propriétés cryptographiques.

```bash
pip install cryptography
python3 tools/verification/verify_backup_format.py
```

**Pourquoi ce harnais existe.** Il a été écrit alors qu'aucun JDK n'était
disponible pour exécuter `BackupManagerTest.kt`. Deux implémentations
indépendantes qui s'accordent sur les mêmes primitives valident la *logique*
du format, ce que ne fait aucune relecture de code.

Il **ne remplace pas** `app/src/test/.../BackupManagerTest.kt` :
- ce script valide le **format et les primitives** ;
- les tests Kotlin valident le **code réellement livré**.

Les deux doivent passer. Le script reste utile comme oracle en cas d'évolution
du format : toute divergence Kotlin ↔ Python signale une régression.

Résultat attendu : `═══ Format validé : 16/16 contrôles réussis ═══`
