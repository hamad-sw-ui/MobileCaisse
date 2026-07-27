package com.reconsiliation.caisse.utils

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseUtil {
    // Secret salt to "blind" the algorithm. 
    // IMPORTANT: In a real production app, this should be obfuscated further or split.
    private const val SECRET_SALT = "M0M0_C41SS3_V1_PRO_S3CR3T_2024_K3Y"
    private const val ALGO = "HmacSHA256"

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
        val data = "$phone|$expiryStr|$SECRET_SALT"
        return calculateHmac(data)
    }

    private fun calculateHmac(data: String): String {
        val secretKey = SecretKeySpec(SECRET_SALT.toByteArray(StandardCharsets.UTF_8), ALGO)
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

    /**
     * Admin Utility: Use this to generate a key for a customer.
     * You can call this from a hidden debug menu or a separate admin tool.
     */
    fun generateActivationKey(phone: String, months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months)
        val sdf = SimpleDateFormat("ddMMyy", Locale.US)
        val expiryStr = sdf.format(cal.time)
        val sig = generateSignature(PhoneUtil.normalize(phone), expiryStr)
        return "$sig-$expiryStr"
    }
}
