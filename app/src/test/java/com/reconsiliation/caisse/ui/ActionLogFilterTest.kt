package com.reconsiliation.caisse.ui

import com.reconsiliation.caisse.data.local.entity.ActionLogEntity
import com.reconsiliation.caisse.ui.screens.filterActionLogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ActionLogFilterTest {

    private fun createLog(actionType: String, details: String, severity: String = "INFO"): ActionLogEntity {
        return ActionLogEntity(
            date = Date(),
            actionType = actionType,
            details = details,
            userRole = "ADMIN",
            severity = severity
        )
    }

    @Test
    fun `anomalies filter includes future sale anomaly`() {
        val futureLog = createLog("ANOMALY_DETECTED", "FUTURE", "CRITICAL")
        val normalLog = createLog("SALE", "Vente #1")
        val filtered = filterActionLogs(listOf(futureLog, normalLog), "ANOMALIES")
        assertEquals(1, filtered.size)
        assertEquals("ANOMALY_DETECTED", filtered[0].actionType)
        assertEquals("FUTURE", filtered[0].details)
    }

    @Test
    fun `anomalies filter includes duplicate sale anomaly`() {
        val duplicateLog = createLog("ANOMALY_DETECTED", "DUPLICATE", "CRITICAL")
        val filtered = filterActionLogs(listOf(duplicateLog), "ANOMALIES")
        assertEquals(1, filtered.size)
        assertEquals("DUPLICATE", filtered[0].details)
    }

    @Test
    fun `anomalies filter includes high amount anomaly`() {
        val highAmountLog = createLog("ANOMALY_DETECTED", "HIGH_AMOUNT", "WARNING")
        val filtered = filterActionLogs(listOf(highAmountLog), "ANOMALIES")
        assertEquals(1, filtered.size)
        assertEquals("HIGH_AMOUNT", filtered[0].details)
    }

    @Test
    fun `anomalies filter includes date anomaly`() {
        val dateAnomalyLog = createLog("DATE_ANOMALY", "Écart de temps détecté (>30m)", "CRITICAL")
        val filtered = filterActionLogs(listOf(dateAnomalyLog), "ANOMALIES")
        assertEquals(1, filtered.size)
        assertEquals("DATE_ANOMALY", filtered[0].actionType)
    }

    @Test
    fun `anomalies filter excludes non anomaly sale`() {
        val saleLog = createLog("SALE", "Vente #10", "INFO")
        val filtered = filterActionLogs(listOf(saleLog), "ANOMALIES")
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `anomalies filter excludes non anomaly critical log`() {
        val criticalOtherLog = createLog("DELETE_SALE", "Vente #5 supprimée", "CRITICAL")
        val filtered = filterActionLogs(listOf(criticalOtherLog), "ANOMALIES")
        assertTrue(filtered.isEmpty())

        // But critical filter MUST include it
        val criticalFiltered = filterActionLogs(listOf(criticalOtherLog), "CRITICAL")
        assertEquals(1, criticalFiltered.size)
    }

    @Test
    fun `anomalies filter includes historical anomaly with custom French details`() {
        val oldAnomalyLog = createLog("ANOMALY_DETECTED", "Vente dans le futur detectée...", "CRITICAL")
        val filtered = filterActionLogs(listOf(oldAnomalyLog), "ANOMALIES")
        assertEquals(1, filtered.size)
        assertEquals("Vente dans le futur detectée...", filtered[0].details)
    }

    @Test
    fun `ALL filter returns all logs without removing any`() {
        val logs = listOf(
            createLog("SALE", "Vente #1", "INFO"),
            createLog("ANOMALY_DETECTED", "FUTURE", "CRITICAL"),
            createLog("LOGIN", "Connexion ADMIN", "INFO")
        )
        val filtered = filterActionLogs(logs, "ALL")
        assertEquals(3, filtered.size)
    }

    @Test
    fun `existing filters INFO and CRITICAL maintain their semantics`() {
        val logs = listOf(
            createLog("SALE", "Vente #1", "INFO"),
            createLog("ANOMALY_DETECTED", "HIGH_AMOUNT", "WARNING"),
            createLog("DELETE_SALE", "Suppression", "CRITICAL")
        )
        val infoLogs = filterActionLogs(logs, "INFO")
        assertEquals(1, infoLogs.size)
        assertEquals("SALE", infoLogs[0].actionType)

        val criticalLogs = filterActionLogs(logs, "CRITICAL")
        assertEquals(2, criticalLogs.size) // Includes WARNING and CRITICAL
    }

    @Test
    fun `anomalies filter returns empty list when no anomalies present`() {
        val logs = listOf(
            createLog("SALE", "Vente #1", "INFO"),
            createLog("LOGIN", "Connexion ADMIN", "INFO"),
            createLog("SESSION_OPEN", "Ouverture session", "INFO")
        )
        val filtered = filterActionLogs(logs, "ANOMALIES")
        assertTrue(filtered.isEmpty())
    }
}
