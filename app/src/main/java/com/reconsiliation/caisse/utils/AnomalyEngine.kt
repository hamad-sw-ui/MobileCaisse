package com.reconsiliation.caisse.utils

import com.reconsiliation.caisse.data.local.entity.VenteEntity
import java.util.*

object AnomalyEngine {
    /**
     * Analyse une vente pour détecter des comportements suspects.
     */
    fun analyzeVente(vente: VenteEntity, history: List<VenteEntity>): AnomalyResult {
        val now = Date()
        
        // 1. Détection de modification d'heure système (Rétroaction temporelle)
        if (vente.date.after(now)) {
            return AnomalyResult(true, "Vente dans le futur detectée. Heure systeme suspecte.")
        }

        // 2. Détection de ventes répétitives (Suspicion de spam ou doublon)
        val similarSales = history.filter {
            it.amount == vente.amount &&
            Math.abs(it.date.time - vente.date.time) < 30000 // Moins de 30 secondes
        }
        if (similarSales.isNotEmpty()) {
            return AnomalyResult(true, "Vente identique en moins de 30s. Risque de doublon.")
        }

        // 3. Détection de montant anormalement élevé
        if (vente.amount > 500000) {
            return AnomalyResult(false, "Montant inhabituel (>500k). Verification recommandee.", severity = "WARNING")
        }

        return AnomalyResult(false, "OK")
    }
}

data class AnomalyResult(
    val blocked: Boolean,
    val message: String,
    val severity: String = "INFO"
)
