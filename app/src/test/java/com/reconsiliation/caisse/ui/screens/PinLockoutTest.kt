package com.reconsiliation.caisse.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the anti-brute-force lockout of the PIN screen.
 */
class PinLockoutTest {

    @Test
    fun `no lockout before 3 failures`() {
        assertEquals(0L, pinLockoutDelayMs(0))
        assertEquals(0L, pinLockoutDelayMs(1))
        assertEquals(0L, pinLockoutDelayMs(2))
    }

    @Test
    fun `lockout starts at 1 second after 3 failures`() {
        assertEquals(1000L, pinLockoutDelayMs(3))
    }

    @Test
    fun `delay doubles with failures`() {
        assertEquals(2000L, pinLockoutDelayMs(4))
        assertEquals(4000L, pinLockoutDelayMs(5))
        assertEquals(8000L, pinLockoutDelayMs(6))
        assertEquals(16000L, pinLockoutDelayMs(7))
    }

    @Test
    fun `delay is capped at 30 seconds`() {
        assertEquals(30000L, pinLockoutDelayMs(8))
        assertEquals(30000L, pinLockoutDelayMs(20))
        assertEquals(30000L, pinLockoutDelayMs(1000))
    }
}
