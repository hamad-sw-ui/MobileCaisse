package com.reconsiliation.caisse.utils

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseUtil {
    // Secret salt to "blind" the algorithm. 
    // B-024: Fragments de secret pour éviter la recherche textuelle simple.
    private val S1 = "M0M0"
    private val S2 = "_C41SS3"
    private val S3 = "_V1_PRO"
    private val S4 = "_S3CR3T"
    private val S5 = "_2024_K3Y"
    
    private val ALGO = "HmacSHA256"

    private fun getSecret(): String = S1 + S2 + S3 + S4 + S5

    /**
     * Verifies an activation key against a phone number.
     * Format of Key expected: SIGNATURE-EXPIRY (e.g., A1B2C3D4-311225)
     */
    fun verifyKey(phone: String, key: String): Date? {
        try {
            val parts = key.trim().uppercase().split("-")
            if (parts.size != 2) return null
            
            val providedSignature = parts[0]
            val expiryStr = parts[1] // Format: DDMMYY
            
            val normalizedPhone = PhoneUtil.normalize(phone)
            if (normalizedPhone.isEmpty()) return null

            // Re-calculate signature
            val expectedSignature = generateSignature(normalizedPhone, expiryStr)
            
            if (providedSignature == expectedSignature) {
                // Parse expiry date
                val sdf = SimpleDateFormat("ddMMyy", Locale.US)
                val expiryDate = sdf.parse(expiryStr)
                return expiryDate
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Generates a unique signature for a phone and expiry date.
     * This is the same logic you (the admin) would use to generate keys for clients.
     */
    private fun generateSignature(phone: String, expiryStr: String): String {
        val data = "$phone|$expiryStr|${getSecret()}"
        return calculateHmac(data)
    }

    private fun calculateHmac(data: String): String {
        val secretKey = SecretKeySpec(getSecret().toByteArray(StandardCharsets.UTF_8), ALGO)
        val mac = Mac.getInstance(ALGO)
        mac.init(secretKey)
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        // Custom base-32 like encoding for "blindage" and readability
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Avoid 0, 1, I, O
        val sb = StringBuilder()
        for (i in 0 until 8) { // We only need 8 chars for the signature part
            val index = (hash[i].toInt() and 0xFF) % alphabet.length
            sb.append(alphabet[index])
        }
        return sb.toString()
    }
}
