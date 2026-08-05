package com.reconsiliation.caisse.utils

import org.junit.Assert.*
import org.junit.Test

class FeeCalculatorTest {

    @Test
    fun `orange retrait fees should be 1,5 percent`() {
        val amount = 10000.0
        val fees = FeeCalculator.calculatePrecisionFees("ORANGE", amount, "RETRAIT")
        assertEquals(150.0, fees, 0.001)
    }

    @Test
    fun `orange transfert fees should be 0,5 percent`() {
        val amount = 10000.0
        val fees = FeeCalculator.calculatePrecisionFees("ORANGE", amount, "TRANSFERT")
        assertEquals(50.0, fees, 0.001)
    }

    @Test
    fun `mtn retrait fees should be commission plus tax`() {
        val amount = 10000.0
        // commission = 10000 * 0.01 = 100
        // tax = (10000 * 0.002) + 4 = 20 + 4 = 24
        // total = 124
        val fees = FeeCalculator.calculatePrecisionFees("MTN", amount, "RETRAIT")
        assertEquals(124.0, fees, 0.001)
    }

    @Test
    fun `mtn transfert fees should be only tax`() {
        val amount = 10000.0
        // tax = (10000 * 0.002) + 4 = 24
        val fees = FeeCalculator.calculatePrecisionFees("MTN", amount, "TRANSFERT")
        assertEquals(24.0, fees, 0.001)
    }

    @Test
    fun `payment to merchant should have zero fees`() {
        val amount = 5000.0
        assertEquals(0.0, FeeCalculator.calculatePrecisionFees("MTN", amount, "PAIEMENT"), 0.0)
        assertEquals(0.0, FeeCalculator.calculatePrecisionFees("ORANGE", amount, "PAIEMENT"), 0.0)
    }

    @Test
    fun `zero or negative amount should return zero fees`() {
        assertEquals(0.0, FeeCalculator.calculatePrecisionFees("MTN", 0.0, "RETRAIT"), 0.0)
        assertEquals(0.0, FeeCalculator.calculatePrecisionFees("ORANGE", -100.0, "RETRAIT"), 0.0)
    }

    @Test
    fun `calculateMomoFees should use MTN Retrait logic`() {
        val amount = 5000.0
        val feesLegacy = FeeCalculator.calculateMomoFees(amount)
        val feesNew = FeeCalculator.calculatePrecisionFees("MTN", amount, "RETRAIT")
        assertEquals(feesNew, feesLegacy, 0.001)
    }
}
