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
    fun `verifyPin should pass for PIN saved with setup format (salt without suffix)`() = runBlocking {
        // Reproduction du bug de connexion apres configuration initiale :
        // completeSetup hachait avec 100000 iterations mais stockait le sel SANS
        // le suffixe ":iterations". verifyPin partait alors sur 5000 iterations
        // (legacy) et rejetait le PIN correct. Ce test garantit la coherence
        // hash/verification pour tout nouveau format introduit.
        val pin = "1234"
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt) // iterations par defaut = PBKDF2_ITERATIONS_NEW
        val storedSaltNoSuffix = salt // comme completeSetup avant correction

        // Le sel sans suffixe doit resoudre vers les iterations NEW (et non legacy)
        // pour que le hash par defaut soit verifie correctement.
        assertTrue(
            "PIN hashed with default iterations must verify with bare salt",
            SecurityUtil.verifyPin(pin, hash, storedSaltNoSuffix)
        )
        assertFalse(
            "Wrong PIN must still be rejected with bare salt",
            SecurityUtil.verifyPin("9999", hash, storedSaltNoSuffix)
        )
    }

    @Test
    fun `verifyPin should pass with correct PBKDF2 hash and legacy format salt`() = runBlocking {
        val pin = "1234"
        val iterations = 5000
        val salt = SecurityUtil.generateSalt()
        val hash = SecurityUtil.hashPinPbkdf2(pin, salt, iterations)
                // Les sels legacy reels (saveStaff / changePin) portent toujours le
        // suffixe ":iterations" ; un sel nu designe desormais le format NEW.
        val saltWithIterations = "$salt:$iterations"

        assertTrue("Correct PIN with legacy salt should pass", SecurityUtil.verifyPin(pin, hash, saltWithIterations))
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
