package com.reconsiliation.caisse.utils

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class LicenseUtilTest {

    /**
     * Re-implementation of the signature logic for testing.
     * This simulates the ADMIN tool.
     */
    private fun generateTestKey(phone: String, expiryDate: Date): String {
        val sdf = SimpleDateFormat("ddMMyy", Locale.US)
        val expiryStr = sdf.format(expiryDate)
        
        // Secret segments from LicenseUtil
        val secret = "M0M0_C41SS3_V1_PRO_S3CR3T_2024_K3Y"
        val data = "${PhoneUtil.normalize(phone)}|$expiryStr|$secret"
        
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val sb = StringBuilder()
        for (i in 0 until 8) {
            val index = (hash[i].toInt() and 0xFF) % alphabet.length
            sb.append(alphabet[index])
        }
        val sig = sb.toString()
        return "$sig-$expiryStr"
    }

    @Test
    fun `verifyKey should return date for valid key`() {
        val phone = "677112233"
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, 1)
        val expiryDate = calendar.time
        
        val key = generateTestKey(phone, expiryDate)
        
        val result = LicenseUtil.verifyKey(phone, key)
        
        assertNotNull("Valid key should return a date", result)
        val sdf = SimpleDateFormat("ddMMyy", Locale.US)
        assertEquals(sdf.format(expiryDate), sdf.format(result!!))
    }

    @Test
    fun `verifyKey should return null for incorrect phone number`() {
        val phone = "677112233"
        val otherPhone = "699000000"
        val key = generateTestKey(phone, Date())
        
        val result = LicenseUtil.verifyKey(otherPhone, key)
        
        assertNull("Key for other phone should be rejected", result)
    }

    @Test
    fun `verifyKey should return null for tampered signature`() {
        val phone = "677112233"
        val key = generateTestKey(phone, Date())
        val tamperedKey = "FFFFFFFF" + key.substring(8)
        
        val result = LicenseUtil.verifyKey(phone, tamperedKey)
        
        assertNull("Tampered signature should be rejected", result)
    }

    @Test
    fun `verifyKey should return null for malformed key`() {
        assertNull(LicenseUtil.verifyKey("677112233", "INVALIDKEY"))
        assertNull(LicenseUtil.verifyKey("677112233", "ABC-12345-DEF"))
    }
}
