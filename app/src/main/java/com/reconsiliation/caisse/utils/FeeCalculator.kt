package com.reconsiliation.caisse.utils

object FeeCalculator {
    /**
     * Calcule les frais Mobile Money selon l'opérateur et le type d'opération.
     */
    fun calculatePrecisionFees(operator: String, amount: Double, operationType: String): Double {
        if (amount <= 0) return 0.0
        
        return when (operator.uppercase()) {
            "ORANGE" -> when (operationType) {
                "RETRAIT" -> amount * 0.015 // ~1.5%
                "TRANSFERT" -> amount * 0.005 // ~0.5%
                else -> 0.0 // PAIEMENT MARCHAND
            }
            "MTN" -> when (operationType) {
                "RETRAIT" -> {
                    val commission = amount * 0.01 // ~1%
                    val tax = (amount * 0.002) + 4.0 // 0.2% + 4F
                    commission + tax
                }
                "TRANSFERT" -> (amount * 0.002) + 4.0 // Uniquement les taxes régulatoires
                else -> 0.0 // PAIEMENT MARCHAND
            }
            else -> 0.0
        }
    }

    /**
     * Ancien calcul (fallback ou simplifié)
     */
    fun calculateMomoFees(amount: Double): Double {
        return calculatePrecisionFees("MTN", amount, "RETRAIT")
    }
}
