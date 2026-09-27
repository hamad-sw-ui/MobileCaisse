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
        assertNull(result.code)
    }

    @Test
    fun `future sale should be blocked`() {
        val futureDate = Date(System.currentTimeMillis() + 1000000)
        val vente = createVente(1000.0, futureDate)
        val result = AnomalyEngine.analyzeVente(vente, emptyList())
        assertTrue("Future sale must be blocked", result.blocked)
        assertTrue(result.message.contains("futur"))
        assertEquals(AnomalyEngine.CODE_FUTURE, result.code)
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
        assertEquals(AnomalyEngine.CODE_DUPLICATE, result.code)
    }

    @Test
    fun `large amount should trigger warning but not block`() {
        val vente = createVente(600000.0, Date())
        val result = AnomalyEngine.analyzeVente(vente, emptyList())
        assertFalse("Large amount should not be blocked", result.blocked)
        assertEquals("WARNING", result.severity)
        assertTrue(result.message.contains(">500k"))
        assertEquals(AnomalyEngine.CODE_HIGH_AMOUNT, result.code)
    }

    @Test
    fun `sms clock identical timestamp is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(systemTime, systemTime))
    }

    @Test
    fun `sms clock 1 minute before is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(systemTime - ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock 29 minutes before is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(systemTime - 29 * ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock exactly 30 minutes before is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(systemTime - 30 * ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock 31 minutes before is an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertTrue(AnomalyEngine.isSmsClockAnomaly(systemTime - 31 * ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock 31 minutes after is an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertTrue(AnomalyEngine.isSmsClockAnomaly(systemTime + 31 * ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock null timestamp is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(null, systemTime))
    }

    @Test
    fun `sms clock exactly 30 minutes after is not an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertFalse(AnomalyEngine.isSmsClockAnomaly(systemTime + 30 * ONE_MINUTE_MS, systemTime))
    }

    @Test
    fun `sms clock 31 minutes in the other direction is an anomaly`() {
        val systemTime = FIXED_SYSTEM_TIME
        assertTrue(AnomalyEngine.isSmsClockAnomaly(systemTime + 31 * ONE_MINUTE_MS, systemTime))
    }

    companion object {
        private const val FIXED_SYSTEM_TIME = 1_700_000_000_000L
        private const val ONE_MINUTE_MS = 60_000L
    }
}
