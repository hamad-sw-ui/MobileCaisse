package com.reconsiliation.caisse.utils

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SecurityUtilTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `kotlin Base64 should be compatible with standard Base64 NO_WRAP`() {
        // "Hello" encoded with android.util.Base64.NO_WRAP is "SGVsbG8="
        val legacyEncoded = "SGVsbG8="
        val decoded = Base64.decode(legacyEncoded)
        assertEquals("Hello", String(decoded))

        // Verify encoding includes padding and no newlines (standard)
        val reEncoded = Base64.encode("Hello".toByteArray())
        assertEquals(legacyEncoded, reEncoded)
        
        // "Hell" requires 2 chars of padding: "SGVsbA=="
        val legacyEncoded2 = "SGVsbA=="
        assertEquals(legacyEncoded2, Base64.encode("Hell".toByteArray()))
    }

    @Test
    fun `verifyPin should pass with correct PBKDF2 hash and new format salt`() = runBlocking {
        val pin = "1234"
        val iterations = 100_000
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt, iterations)
        val saltWithIterations = "$salt:$iterations"
        
        assertTrue("Correct PIN with new format salt should pass", SecurityUtil.verifyPin(pin, hash, saltWithIterations))
    }

    @Test
    fun `verifyPin should pass with correct PBKDF2 hash and legacy format salt`() = runBlocking {
        val pin = "1234"
        val iterations = 5000
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt, iterations)
        
        assertTrue("Correct PIN with legacy salt should pass", SecurityUtil.verifyPin(pin, hash, salt))
    }

    @Test
    fun `verifyPin should fail with incorrect PIN and salt`() = runBlocking {
        val pin = "1234"
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt)
        
        assertFalse("Incorrect PIN with salt should fail", SecurityUtil.verifyPin("5678", hash, salt))
    }

    @Test
    fun `verifyPin should pass with correct legacy SHA-256 hash and null salt`() = runBlocking {
        val pin = "1234"
        val hash = SecurityUtil.hashPin(pin)
        
        assertTrue("Correct legacy PIN without salt should pass", SecurityUtil.verifyPin(pin, hash, null))
    }

    @Test
    fun `verifyPin should fail with incorrect legacy PIN and null salt`() = runBlocking {
        val pin = "1234"
        val hash = SecurityUtil.hashPin(pin)
        
        assertFalse("Incorrect legacy PIN without salt should fail", SecurityUtil.verifyPin("5678", hash, null))
    }
}
