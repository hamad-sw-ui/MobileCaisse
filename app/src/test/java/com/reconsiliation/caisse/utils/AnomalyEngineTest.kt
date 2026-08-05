package com.reconsiliation.caisse.utils

import com.reconsiliation.caisse.data.local.entity.VenteEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class AnomalyEngineTest {

    private fun createVente(amount: Double, date: Date) = VenteEntity(
        amount = amount,
        date = date,
        description = "Test",
        paymentMethod = "CASH",
        status = "CONFIRMED"
    )

    @Test
    fun `normal sale should be OK`() {
        val vente = createVente(1000.0, Date())
        val result = AnomalyEngine.analyzeVente(vente, emptyList())
        assertFalse(result.blocked)
        assertEquals("OK", result.message)
        assertEquals("INFO", result.severity)
    }

    @Test
    fun `future sale should be blocked`() {
        val futureDate = Date(System.currentTimeMillis() + 1000000)
        val vente = createVente(1000.0, futureDate)
        val result = AnomalyEngine.analyzeVente(vente, emptyList())
        assertTrue("Future sale must be blocked", result.blocked)
        assertTrue(result.message.contains("futur"))
    }

    @Test
    fun `duplicate sale in short time should be blocked`() {
        val baseTime = System.currentTimeMillis() - 60000 // 1 min ago
        val history = listOf(createVente(5000.0, Date(baseTime)))
        val current = createVente(5000.0, Date(baseTime + 5000)) // 5s after first one, but still in past
        
        val result = AnomalyEngine.analyzeVente(current, history)
        
        assertTrue("Duplicate sale must be blocked", result.blocked)
        assertTrue("Message should mention duplicate (got: ${result.message})", 
            result.message.contains("doublon", ignoreCase = true))
    }

    @Test
    fun `large amount should trigger warning but not block`() {
        val vente = createVente(600000.0, Date())
        val result = AnomalyEngine.analyzeVente(vente, emptyList())
        assertFalse("Large amount should not be blocked", result.blocked)
        assertEquals("WARNING", result.severity)
        assertTrue(result.message.contains(">500k"))
    }
}
