# 📐 RAPPORT DE CONCEPTION & ÉTUDE D'ARCHITECTURE — J6.8 / B-077

**Sujet :** Étude du passage de la représentation monétaire de `Double` (virgule flottante) vers un type exact (`Long` en centimes/FCFA, `BigDecimal` ou Value Class `Money`).  
**Projet :** MobileCaisse / CAISSE  
**Branche :** `arena/019fa5ec-mobilecaisse`  
**Date :** 2026-10-04  
**Auteur :** AI-DOS 3.0  
**Statut du rapport :** DOCUMENTATION PURE / ÉTUDE DE FAISABILITÉ READ-ONLY  
**DÉCISION HUMAINE REQUISE :** Oui (aucune modification de code effectuée dans ce lot).

---

## 1. CONTEXTE

MobileCaisse est une application de gestion de caisse et de stock pour terminaux Android opérant principalement en zone Franc CFA (XOF / XAF — Cameroun, Afrique de l'Ouest et Centrale).

Dans l'architecture actuelle, **l'intégralité des montants monétaires** (prix de vente, prix d'achat, chiffres d'affaires, montants de caisse, dépenses, dettes clients, créances fournisseurs, frais Mobile Money) est représentée par le type `Double` (nombre à virgule flottante IEEE 754 de 64 bits).

Bien que le Franc CFA ne possède aucune sous-unité monétaire en circulation physique (le plus petit jeton légal est de 1 FCFA), l'utilisation du type `Double` introduit des risques théoriques et pratiques d'imprécision arithmétique (ex: `0.1 + 0.2 = 0.30000000000000004`), des accumulations d'erreurs d'arrondi sur de grands volumes de transactions et une ambiguïté sémantique entre les **montants monétaires** et les **quantités physiques** de produits (également stockées en `Double`).

L'objectif de cette étude (B-077 / J6.8) est de mesurer exhaustivement l'impact d'un changement de représentation monétaire, de comparer les options d'architecture envisageables et de soumettre une recommandation motivée au responsable de projet avant toute décision d'implémentation.

---

## 2. OBJECTIF DE L'ÉTUDE

1. **Inventorieur exhaustivement** l'ensemble des entités, DAOs, Repositories, ViewModels, moteurs de calcul, interfaces utilisateur et modules de sauvegarde manipulant des données numériques.
2. **Classifier** de manière étanche les valeurs entre **Monnaie**, **Taux/Pourcentage**, **Quantité Physique** et **Autres Mesures**.
3. **Évaluer 4 options techniques** de représentation monétaire (Double encapsulé, Long FCFA entiers, Long centimes, BigDecimal) ainsi que le rôle d'une Value Class `Money`.
4. **Mesurer l'impact** sur la base de données Room (la base Room comporte 22 entités au total, dont 16 contiennent des champs Double, 13 contiennent des champs à valeur monétaire, 1 contient un taux/pourcentage et 2 contiennent uniquement des quantités physiques), les migrations SQLite, le module `BackupManager` v2 et la synchronisation Cloud Google Drive.
5. **Dresser une matrice comparative** multi-critères.
6. **Formuler une recommandation d'architecture** claire à soumettre à l'arbitrage humain.

---

## 3. ÉTAT ACTUEL DE L'APPLICATION

### 3.1 Déductions & Atténuations Existant
* **Nature du FCFA :** Les montants manipulés par les commerçants sont des nombres entiers (ex: `1 500 FCFA`, `25 000 FCFA`).
* **Atténuation actuelle (`KNOWN_LIMITATIONS.md` §6) :** Les opérations d'addition/soustraction sur des entiers représentés en `Double` (jusqu'à $2^{53} - 1 \approx 9 \times 10^{15}$) restent rigoureusement exactes en norme IEEE 754.
* **Formatage UI :** L'UI applique un formatage d'arrondi d'affichage (`String.format("%.0f FCFA", amount)` ou `roundToInt()`).
* **Facteur de risque principal :** Les opérations de division/multiplication (calcul de taxes TVA, remises en %, commissions Mobile Money `FeeCalculator`, calcul de marge brute) génèrent des décimales qui, si elles sont réintégrées dans la base sans politique d'arrondi explicite, peuvent créer de légers écarts de centimes virtuels.

---

## 4. INVENTAIRE EXHAUSTIF DU CODE

Une inspection complète en lecture seule de la codebase Kotlin a permis d'identifier tous les usages numériques liés directement ou indirectement aux montants et prix.

### 4.1 Entités Room (`data/local/entity/`) — Classification des 22 Entités DB

> **Précision de comptage Room :** La base de données Room comporte **22 entités au total**, dont **16 contiennent au moins un champ `Double`**, **13 contiennent au moins un champ à valeur monétaire**, **1 contient uniquement un taux/pourcentage (`BoutiqueEntity.taxRate`)**, **2 contiennent uniquement des quantités physiques (`RecipeEntity`, `StockMovementEntity`)**, et **6 entités ne contiennent aucun champ `Double` (`ActionLogEntity`, `CategoryEntity`, `ProcessedSmsEntity`, `SmsErrorEntity`, `StaffEntity`, `SubscriptionEntity`)**.  
> **Formules obligatoires de cohérence :** $22 = 16 + 6$ et $16 = 13 \text{ (monétaires)} + 1 \text{ (taux)} + 2 \text{ (quantités)}$.

| Table SQLite | Classe Entité | Champ Kotlin | Type Actuel | Signification Métier | Unité | Destination / Traitement |
|---|---|---|---|---|---|---|
| `audits` | `AuditEntity` | `totalDiscrepancy` | `Double` | Valeur financière totale des pertes/gains de stock | FCFA | Calcul d'écart financier d'inventaire |
| `audit_items` | `AuditItemEntity` | `unitPrice` | `Double` | Prix de vente unitaire du produit audité | FCFA | Valorisation d'écart |
| `audit_items` | `AuditItemEntity` | `purchasePrice` | `Double` | Prix d'achat unitaire du produit audité | FCFA | Valorisation de perte au coût d'achat |
| `audit_items` | `AuditItemEntity` | `systemQuantity` | `Double` | Quantité théorique en stock | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `audit_items` | `AuditItemEntity` | `physicalQuantity` | `Double` | Quantité physique comptée | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `boutique` | `BoutiqueEntity` | `taxRate` | `Double` | Taux de taxe par défaut (ex: `19.25`) | % | **NON MONÉTAIRE** (Taux/Pourcentage) |
| `closures` | `ClosureEntity` | `theoreticalCash` | `Double` | Espèces attendues lors de la clôture | FCFA | Total de caisse théorique |
| `closures` | `ClosureEntity` | `actualCash` | `Double` | Espèces réelles comptées lors de la clôture | FCFA | Déclaration vendeur |
| `closures` | `ClosureEntity` | `theoreticalMomo` | `Double` | MoMo attendu lors de la clôture | FCFA | Total MoMo théorique |
| `closures` | `ClosureEntity` | `actualMomo` | `Double` | MoMo réel déclaré à la clôture | FCFA | Déclaration MoMo |
| `closures` | `ClosureEntity` | `discrepancy` | `Double` | Écart de clôture (réel - attendu) | FCFA | Manque ou surplus de caisse |
| `customers` | `CustomerEntity` | `totalDebt` | `Double` | Cumul des dettes impayées du client | FCFA | Gestion créances clients |
| `customers` | `CustomerEntity` | `totalSpent` | `Double` | Cumul historique des achats du client | FCFA | Fidélité & statistiques |
| `customers` | `CustomerEntity` | `creditBalance` | `Double` | Solde d'avoir / crédit du client | FCFA | Avoirs réutilisables en caisse |
| `expenses` | `ExpenseEntity` | `amount` | `Double` | Montant d'une dépense de caisse | FCFA | Sortie de caisse |
| `price_history`| `PriceHistoryEntity`| `oldPrice` | `Double` | Ancien prix de vente ou d'achat | FCFA | Historique des prix |
| `price_history`| `PriceHistoryEntity`| `newPrice` | `Double` | Nouveau prix de vente ou d'achat | FCFA | Historique des prix |
| `recipes` | `RecipeEntity` | `quantityRequired` | `Double` | Quantité d'ingrédient déduite par recette | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `repayments` | `RepaymentEntity` | `amount` | `Double` | Montant d'un remboursement de dette | FCFA | Encaissement remboursement |
| `sessions` | `SessionEntity` | `openingBalance` | `Double` | Fond de roulement initial de session | FCFA | Ouverture de session |
| `sessions` | `SessionEntity` | `closingBalance` | `Double` | Fond de caisse à la fermeture | FCFA | Fermeture de session |
| `sessions` | `SessionEntity` | `expectedBalance` | `Double` | Total espèces théoriquement attendu | FCFA | `openingBalance + totalCashSales - totalExpenses` |
| `sessions` | `SessionEntity` | `totalCashSales` | `Double` | Cumul ventes espèces de la session | FCFA | Agrégat de session |
| `sessions` | `SessionEntity` | `totalMomoSales` | `Double` | Cumul ventes MoMo de la session | FCFA | Agrégat de session |
| `sessions` | `SessionEntity` | `totalExpenses` | `Double` | Cumul dépenses de la session | FCFA | Agrégat de session |
| `stock` | `StockEntity` | `quantity` | `Double` | Stock en magasin (ex: `12.5 kg`) | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `stock` | `StockEntity` | `unitPrice` | `Double` | Prix de vente unitaire en détail/gros | FCFA | Tarification article |
| `stock` | `StockEntity` | `purchasePrice` | `Double` | Prix d'achat unitaire | FCFA | Coût de revient & calcul de marge |
| `stock` | `StockEntity` | `vipPrice` | `Double` | Tarification spéciale VIP | FCFA | Tarification alternative |
| `stock` | `StockEntity` | `alertThreshold` | `Double` | Seuil d'alerte stock bas | Quantité | **NON MONÉTAIRE** (Seuil physique) |
| `stock` | `StockEntity` | `conversionFactor`| `Double` | Facteur de conversion carton -> détail | Taux | **NON MONÉTAIRE** (Ratio physique) |
| `stock` | `StockEntity` | `retailPrice` | `Double` | Prix de vente à l'unité de détail | FCFA | Tarification dépaquetée |
| `stock_movements`|`StockMovementEntity`|`quantity` | `Double` | Quantité déplacée/ajustée | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `stock_movements`|`StockMovementEntity`|`balanceAfter` | `Double` | Solde de stock après mouvement | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `suppliers` | `SupplierEntity` | `totalDebt` | `Double` | Cumul dette envers le fournisseur | FCFA | Gestion dettes fournisseurs |
| `supplies` | `SupplyEntity` | `quantity` | `Double` | Quantité approvisionnée | Quantité | **NON MONÉTAIRE** (Unité physique) |
| `supplies` | `SupplyEntity` | `purchasePrice` | `Double` | Prix d'achat unitaire d'approvisionnement | FCFA | Coût d'achat |
| `ventes` | `VenteEntity` | `amount` | `Double` | Montant total de la vente | FCFA | `amountCash + amountMomo + credit` |
| `ventes` | `VenteEntity` | `amountCash` | `Double` | Part payée en espèces | FCFA | Ventilation règlement |
| `ventes` | `VenteEntity` | `amountMomo` | `Double` | Part payée en Mobile Money | FCFA | Ventilation règlement |
| `ventes` | `VenteEntity` | `fees` | `Double` | Frais de transaction MoMo | FCFA | Frais opérateur intégrés |
| `ventes` | `VenteEntity` | `discount` | `Double` | Montant de la remise accordée | FCFA | Déduction commerciale |
| `ventes` | `VenteEntity` | `taxAmount` | `Double` | Montant de taxe TVA appliqué | FCFA | Taxe collectée |
| `vente_items` | `VenteItemEntity` | `quantity` | `Double` | Quantité vendue (ex: `2.0`, `1.5`) | Quantité | **NON MONÉTAIRE** (Quantité vendue) |
| `vente_items` | `VenteItemEntity` | `unitPrice` | `Double` | Prix unitaire pratiqué à la vente | FCFA | Prix unitaire figé |
| `vente_items` | `VenteItemEntity` | `purchasePrice` | `Double` | Prix d'achat unitaire au moment de la vente | FCFA | Capture de marge exacte |

### 4.2 DAOs (`data/local/dao/`)
* **`VenteDao` :** Requêtes SQL d'agrégation `SUM(amount)`, `SUM(amountCash)`, `SUM(amountMomo)`, `findPendingMomoSale(amount: Double)`.
* **`CustomerDao` :** `updateDebt(id, amount: Double)`, `updateCreditBalance(id, delta: Double)`.
* **`ExpenseDao` :** `getTotalExpenses(...) : Flow<Double?>`.
* **`StockDao` :** `updateQuantity(id, delta: Double)`.
* **`SupplierDao` :** `updateDebt(id, amount: Double)`.

### 4.3 Repositories (`data/repository/`)
* **`SalesRepository` :** Calculs de profitabilité `getProfitFlow`, clôture de session, remboursements de dettes `addRepayment`, calcul d'attendu de caisse `expectedSessionCash(openingBalance, cashSales, expenses)`.
* **`InventoryRepository` :** Valorisation du stock `getStockValuation() : Flow<Pair<Double, Double>>` (prix d'achat total vs prix de vente total).
* **`MainRepository` :** Pontage d'import/export de sauvegarde, souscription MoMo.

### 4.4 Moteurs Métier & Parsers
* **`FeeCalculator` (`utils/FeeCalculator.kt`) :** Calcule les commissions/taxes MoMo : `amount * 0.015`, `(amount * 0.002) + 4.0`.
* **`SmsParser` (`sms/SmsParser.kt`) :** Parsing regex d'un SMS entrant -> `ParsedSms(amount: Double)`.
* **`AnomalyEngine` (`data/domain/AnomalyEngine.kt`) :** Contrôle des seuils de ventes suspectes (`saleAmount > 1_000_000.0`).
* **`EscPosPrinter` (`platform/EscPosPrinter.kt`) :** Formate et imprime les lignes de tickets : `printItemLine(name, qty: Double, price: Double)`.

---

## 5. CLASSIFICATION STRICTE DES TYPES NUMÉRIQUES

Afin de prévenir toute erreur de conception consistant à migrer "tous les `Double` du projet", nous établissons une **taxonomie obligatoire à 4 catégories** :

```
                  ┌──────────────────────────────────────────────┐
                  │          TYPES NUMÉRIQUES DU PROJET          │
                  └──────────────────────┬───────────────────────┘
                                         │
     ┌───────────────────┬───────────────┴───────────────┬───────────────────┐
     ▼                   ▼                               ▼                   ▼
┌─────────┐    ┌───────────────────┐           ┌───────────────────┐   ┌───────────┐
│ MONNAIE │    │ TAUX/POURCENTAGE  │           │ QUANTITÉ PHYSIQUE │   │  MESURE/  │
│ (FCFA)  │    │ (Non-monétaire)   │           │   (Non-monétaire) │   │   AUTRE   │
└────┬────┘    └─────────┬─────────┘           └─────────┬─────────┘   └─────┬─────┘
     │                   │                               │                 │
 • Prix unitaire     • taxRate (19.25%)              • quantity (12.5 kg) • ID
 • Montant vente     • conversionFactor (12.0)       • systemQuantity   • Timestamp
 • Solde/Dette       • commissionRate (1.5%)         • alertThreshold   • Score SMS
 • Fond de caisse
```

### 5.1 Règle d'or de la Classification
1. **Seule la catégorie MONNAIE est candidate** au changement de représentation vers un type monétaire exact (`Long` / `BigDecimal` / `Money`).
2. **La catégorie QUANTITÉ PHYSIQUE doit impérativement rester en `Double`** (ou type à virgule flottante) pour préserver la possibilité de vendre des produits au poid ou au volume (ex: `0.75 kg` de sucre, `1.5 L` d'huile).
3. **La catégorie TAUX/POURCENTAGE doit rester en `Double`** (ou `BigDecimal`) pour conserver la précision des ratios réglementaires ou fiscaux (ex: TVA à `19.25%`).

---

## 6. ANALYSE DES CALCULS & PIÈGES ARITHMÉTIQUES

| Opération Métier | Ingrédients | Formule Actuelle (`Double`) | Risque en `Double` | Comportement en `Long` FCFA | Comportement en `BigDecimal` |
|---|---|---|---|---|---|
| **Total Ligne Vente** | Quantité × Prix Unitaire | `qty * unitPrice` | `0.3 * 1500 = 450.0` (Sûr) | `qtyDouble * unitPriceLong` -> `round()` | `BigDecimal(qty) * BigDecimal(unitPrice)` |
| **Total Panier Vente** | $\sum$ (Lignes) - Remise + Taxes + Frais | `sum(items) - discount + tax + fees` | Écart d'arrondi sur cumuls longs | Somme exacte d'entiers | Somme exacte avec `scale = 2` |
| **Solde de Caisse** | Ouverture + Ventes - Dépenses | `opening + cash - expenses` | Aucun si montants entiers | Somme/soustraction d'entiers exacte | Somme/soustraction exacte |
| **Frais MoMo (MTN)** | Montant × 0.2% + 4 F | `(amount * 0.002) + 4.0` | `1500 * 0.002 + 4 = 7.0` | `(amountLong * 2 + 500) / 1000 + 4` | `amount.multiply(0.002).add(4)` |
| **Taxe TVA (19.25%)** | Total HT × TaxRate / 100 | `totalHt * (taxRate / 100.0)` | Résultat décimal (ex: `192.5 F`) | Exige un arrondi explicite (`Math.round`) | Exige `setScale(0, RoundingMode.HALF_UP)` |
| **Marge Brute** | (Prix Vente - Prix Achat) × Qty | `(unitPrice - purchasePrice) * qty` | Écarts cumulés légers | Calcul exact du gain net | Calcul exact |

---

## 7. ANALYSE DES OPTIONS TECHNIQUES

### 7.1 OPTION A : Conserver `Double` + Inline Value Class `Money` & Tolérance d'Arrondi

* **Concept :** Le stockage Room reste en `Double`. Le code Kotlin utilise une Inline Value Class `value class Money(val amount: Double)` pour apporter le typage fort, empêcher la confusion entre quantité et monnaie, et centraliser les arrondis (`amount.roundToInt().toDouble()`).
* **Avantages :**
  * **0 migration de base de données Room** (schéma SQLite inchangé).
  * **100% rétrocompatible** avec toutes les sauvegardes existantes (`BackupManager` v2 et Cloud).
  * Typage fort à la compilation (`Money` ne peut pas être additionné à `Quantity`).
  * Coût d'allocation NUL au runtime (Inlining JVM).
* **Inconvénients :**
  * Le stockage physique reste en virgule flottante IEEE 754.
  * Les requêtes SQLite d'agrégation (`SUM(amount)`) continuent d'exécuter des additions flottantes côté SQLite.
* **Adéquation FCFA :** **Excellente** (le FCFA n'ayant pas de centimes, l'arrondi entier dans la Value Class élimine 100% des anomalies perçues).

---

### 7.2 OPTION B : `Long` représentant des FCFA Entiers (`1 FCFA = 1 L`)

* **Concept :** Tous les montants monétaires stockés en base et manipulés dans le domaine deviennent des `Long` (ex: `1500 L` = 1 500 FCFA).
* **Avantages :**
  * Arithmétique 100% exacte et déterministe (additions/soustractions entières parfaites).
  * Agrégation SQLite `SUM(amount)` 100% exacte.
  * Modèle conceptuel d'une simplicité biblique pour le FCFA.
* **Inconvénients :**
  * **Refonte lourde du schéma Room :** Modification des 13 tables SQLite comportant des champs monétaires.
  * **Rupture de compatibilité des sauvegardes :** Les anciennes sauvegardes contenant des `Double` nécessiteraient un script de conversion complexe lors de la restauration.
  * Perte de la capacité théorique à gérer des centimes si l'application s'exportait dans une zone à centimes (ex: Euro, Dollar).
  * Nécessite de traiter la multiplication d'une quantité `Double` (ex: `1.5 kg`) par un prix `Long` (ex: `1200 FCFA`) via `Math.round(qty * unitPrice)`.

---

### 7.3 OPTION C : `Long` représentant des Centimes (`1 FCFA = 100 L`)

* **Concept :** Tous les montants sont stockés en centimes de FCFA (ex: `1500 FCFA` = `150000 L`).
* **Avantages :**
  * Exactitude absolue et compatibilité internationale théorique (norme bancaire standard ISO 4217).
* **Inconvénients :**
  * **Complexité et pièges élevés :** Risque permanent de confusion d'échelle (afficher `150 000` au lieu de `1 500` à l'écran si un `/ 100` est omis).
  * **Incompatibilité avec la réalité FCFA :** Aucun commerçant ni utilisateur ne raisonne en centimes de FCFA.
  * **Piège de la division entière :** `150000L / 100L` peut introduire des erreurs de troncature si mal parenthésé.
  * **Impact Room maximal :** Migration des 13 tables monétaires + multiplication par 100 de toutes les données historiques.

---

### 7.4 OPTION D : `BigDecimal` avec Room TypeConverters

* **Concept :** Utilisation de `java.math.BigDecimal` dans le code Kotlin et conversion en `String` ou `Double` dans SQLite via des `@TypeConverter` Room.
* **Avantages :**
  * Précision arithmétique arbitraire exacte avec contrôle explicite du mode d'arrondi (`RoundingMode.HALF_UP`).
* **Inconvénients :**
  * **Pénalité de performance et surcoût mémoire :** `BigDecimal` crée des objets sur le Tas (Heap) à chaque opération arithmétique (pas d'inlining possible).
  * **Verbosité du code :** `a.add(b.multiply(c))` au lieu de `a + b * c`.
  * Requêtes SQLite complexes si stockage sous forme de `String` (`SUM()` ne fonctionne pas directement sur du texte sans cast).

---

## 8. ANCILLAIRE : L'INLINE VALUE CLASS `MONEY`

Une Inline Value Class Kotlin permet de créer un type dédié sans aucun surcoût d'instanciation runtime :

```kotlin
@JvmInline
value class Money(val rawAmount: Double) {
    val roundedFcfa: Long get() = Math.round(rawAmount)
    
    operator fun plus(other: Money): Money = Money(this.rawAmount + other.rawAmount)
    operator fun minus(other: Money): Money = Money(this.rawAmount - other.rawAmount)
    operator fun times(quantity: Double): Money = Money(this.rawAmount * quantity)
    
    fun formatDisplay(): String = "${String.format("%,d", roundedFcfa)} FCFA"
}
```

### Combinaisons possibles :
1. **Value Class `Money(Double)` :** Sécurise le typage dans le code applicatif, élimine les erreurs d'affichage, conserve le stockage Room intact (0 migration).
2. **Value Class `Money(Long)` :** Sécurise le typage tout en s'appuyant sur un stockage entier en base.

---

## 9. IMPACT SUR ROOM & MIGRATIONS BASE DE DONNÉES

Si une migration de stockage vers `Long` (Option B ou C) ou `BigDecimal` (Option D) était décidée, voici l'impact précis sur la base de données Room :

```
             IMPACT MIGRATION ROOM (13 TABLES MONÉTAIRES SUR 22 ENTITÉS)
┌─────────────────────────────────────────────────────────────────────────┐
│ [tables monétaires : 13 tables sur 22 entités Room au total]           │
│  audits, audit_items, closures, customers, expenses, price_history,     │
│  repayments, sessions, stock, suppliers, supplies, ventes, vente_items  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                 RÉÉCRITURE DES 13 TABLES MONÉTAIRES
                                     │
     ┌───────────────────────────────┴───────────────────────────────┐
     ▼                                                               ▼
CRÉATION TABLES TEMPORAIRES                             MIGRATION DES DONNÉES
(ex: `ventes_new` avec `amount INTEGER`)               `INSERT INTO ventes_new SELECT id, 
                                                        CAST(ROUND(amount) AS INTEGER)...`
                                     │
                                     ▼
                        DROP & RENAME DE TABLES
                        + RECONSTRUCTION DES INDEX
```

### 9.1 Risques de la Migration Room
* **Augmentation de version DB :** Passage obligatoire de version DB (ex: v28 -> v29).
* **Temps d'exécution :** Sur un terminal d'entrée de gamme avec une base de 50 000 ventes, la migration SQLite peut durer plusieurs secondes.
* **Risque d'échec de migration :** Si une colonne nullable contient `NULL`, un `CAST` SQLite inapproprié peut corrompre la table.

---

## 10. IMPACT SUR BACKUPMANAGER & RESTAURATION

Le module `BackupManager` v2 génère des sauvegardes chiffrées `.db.enc` qui contiennent **la base SQLite réelle du client**.

### 10.1 Scénarios de Rétrocompatibilité

```
Scénario A : Ancienne sauvegarde (Double)  ───► Nouvelle App (Long)
   └── EXIGE une migration Room lors du restoreDatabase() ou un script de conversion explicite.

Scénario B : Nouvelle sauvegarde (Long)    ───► Ancienne App (Double)
   └── CRASH ou corruption : l'ancienne application ne saura pas lire des entiers si elle attend des DOUBLE.
```

### 10.2 Impact Cloud Backup
La sauvegarde distante Cloud Google Drive (J6.7b) transporte le conteneur binaire `.db.enc`. Elle est neutre vis-à-vis du contenu, mais la restauration obéit aux mêmes contraintes de schéma que la restauration locale.

---

## 11. MATRICE COMPARATIVE MULTI-CRITÈRES

Échelle d'évaluation :  
`++` Très Favorable | `+` Favorable | `=` Neutre | `-` Défavorable | `--` Très Défavorable

| Critère d'Évaluation | Option A : `Double` + Value Class `Money` | Option B : `Long` (FCFA Entiers) | Option C : `Long` (Centimes) | Option D : `BigDecimal` |
|---|:---:|:---:|:---:|:---:|
| **Exactitude Mathématique FCFA** | `+` (Arrondi entier) | `++` (Exacte) | `++` (Exacte) | `++` (Exacte) |
| **Simplicité du Code Kotlin** | `++` (Naturel) | `+` (Naturel) | `-` (Piège /100) | `--` (Verbeux) |
| **Impact Base de Données (Room)** | `++` (**0 Migration**) | `--` (13 Tables) | `--` (13 Tables) | `-` (Converters) |
| **Rétrocompatibilité Sauvegardes** | `++` (**100% Compatible**) | `--` (Rupture) | `--` (Rupture) | `-` (Rupture) |
| **Coût/Effort de Développement** | `++` (Très Faible) | `--` (Très Élevé) | `--` (Très Élevé) | `-` (Moyen) |
| **Risque de Régression applicative**| `++` (Quasi Nul) | `--` (Élevé) | `--` (Très Élevé) | `-` (Moyen) |
| **Adaptation à la réalité FCFA** | `++` (Parfaite) | `++` (Parfaite) | `--` (Inadaptée) | `=` (Surdimensionné) |
| **Inlining & Performance CPU/RAM** | `++` (0 Allocation) | `+` (Primitif) | `+` (Primitif) | `--` (Heap Alloc) |
| **Réversibilité** | `++` (Totale) | `-` (Difficile) | `--` (Difficile) | `-` (Difficile) |

---

## 12. SCÉNARIOS DE STRATÉGIE PROPOSÉS

### Scénario 1 : Statu Quo Maitrisé avec Helpers d'Arrondi (Recommandé Phase Immédiate)
* **Description :** Conserver la structure `Double` en base de données. Introduire un helper d'arrondi monétaire centralisé (`MoneyUtils.roundFcfa(amount: Double): Double`) pour sécuriser les calculs de taxes, remises et commissions.
* **Effort :** Minimal (quelques heures).
* **Risque :** Nul.

### Scénario 2 : Encapsulation Progressive via Inline Value Class `Money` (Recommandé Architecture Cible)
* **Description :** Créer `value class Money(val amount: Double)` dans le domaine applicatif sans modifier le schéma de la base Room. Progressivement typer les ViewModels et DTOs avec `Money`.
* **Effort :** Faible à Moyen.
* **Risque :** Quasiment nul.

### Scénario 3 : Refonte Globale DB & Domaine vers `Long` FCFA (Déconseillé en l'état)
* **Description :** Réécrire les 13 entités Room comportant des champs monétaires (sur les 22 entités de la base), créer la migration v28->v29, adapter tous les DAOs, Repositories, ViewModels et l'UI.
* **Effort :** Très Élevé (Plusieurs semaines, risque majeur de régression sur une application déjà stabilisée et testée à 193/193 tests).
* **Risque :** Majeur.

---

## 13. RECOMMANDATION D'ARCHITECTURE

Based on the evidence audited across the codebase and the specific operational domain (Zone FCFA) :

> [!IMPORTANT]
> **RECOMMANDATION OFFICIELLE : ADOPTER LE SCÉNARIO 1 / 2 (ENCAPSULATION VALUE CLASS SANS MIGRATION DE BASE)**
>
> 1. **Ne pas effectuer de migration lourde des 13 tables Room comportant des champs monétaires (sur 22 entités) vers `Long` ou `BigDecimal` dans l'immédiat.** Cette option est déconseillée au regard des critères étudiés, car le bénéfice arithmétique réel pour le Franc CFA ne compense pas le risque élevé de rupture de migration DB et d'incompatibilité des sauvegardes clients.
> 2. **Scuriser les calculs de fractions (TVA, remises, commissions MoMo)** en appliquant systématiquement un arrondi explicite à l'entier le plus proche (`Math.round()`) au point de calcul.
> 3. **Si un renforcement de typage est souhaité par le responsable de projet**, privilégier l'introduction d'une Inline Value Class `Money(val amount: Double)` dans la couche Domaine, qui garantit un typage étanche à la compilation sans toucher au stockage SQLite.

---

## 14. CONDITIONS PRÉALABLES À TOUTE MIGRATION CODE

Si le responsable de projet décidait malgré cette recommandation d'ordonner la migration physique de la base vers `Long` (Scénario 3) :

1. **Obligation de construire un harnais de test de migration Room** (jeu de tests `MigrationTestHelper` couvrant v28->v29 pour les 13 tables monétaires).
2. **Création d'un convertisseur de sauvegardes legacy** pour transformer les anciennes sauvegardes `Double` lors de l'import.
3. **Campagne de tests non-régression** sur tous les flux de caisse, clôture, dettes, approvisionnements et rapports.

---

## 15. DÉCISION HUMAINE REQUISE

> [!CAUTION]
> **ARBITRAGE DU RESPONSABLE DE PROJET REQUIS**
>
> Aucune ligne de code applicatif n'a été modifiée. Le responsable de projet doit valider l'une des orientations suivantes :
>
> * **Choix A (Suivre la recommandation) :** Valider la clôture du jalon J6.8 / B-077 sur la base de cette étude. Conserver le stockage `Double` sécurisé par des arrondis explicites.
> * **Choix B (Encapsulation Value Class) :** Planifier un lot ultra-léger d'introduction de `value class Money(val amount: Double)` dans le domaine sans migration DB.
> * **Choix C (Migration lourde `Long`) :** Exiger la planification d'un chantier lourd de migration Room (J6.8b).

---

## 16. VALIDATION DES CRITÈRES D'ACCEPTATION DU LOT 1

* [x] **AC-B077-01 :** Rapport d'étude présent et détaillé sous `.ai/REPORTS/analyse_conception_2026-10-04_passage_type_monetaire_exact.md`.
* [x] **AC-B077-02 :** Inventaire exhaustif des 22 entités Room, dont 16 contiennent un Double, 13 contiennent des valeurs monétaires, 1 contient un taux (BoutiqueEntity.taxRate), et 2 contiennent uniquement des quantités physiques (RecipeEntity, StockMovementEntity).
* [x] **AC-B077-03 :** Classification étanche (Monnaie / Taux / Quantité) établie.
* [x] **AC-B077-04 :** Matrice comparative et analyse d'impact Room/Backup/Cloud rédigées.
* [x] **AC-B077-05 :** Recommandation explicite soumise à décision humaine sans aucune modification de code applicatif.

---

## 17. VERDICT

**STUDY READY FOR INDEPENDENT AUDIT**

*(L'étude d'architecture et de conception J6.8 / B-077 LOT 1 est intégralement rédigée sous `.ai/REPORTS/` et prête pour l'audit documentaire indépendant. Aucun code n'a été modifié, aucun commit n'a été créé).*
