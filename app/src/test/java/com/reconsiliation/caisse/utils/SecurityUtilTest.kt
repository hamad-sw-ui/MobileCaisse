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
    fun `verifyPin should pass with correct PBKDF2 hash and salt`() = runBlocking {
        val pin = "1234"
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt)
        
        assertTrue("Correct PIN with salt should pass", SecurityUtil.verifyPin(pin, hash, salt))
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
