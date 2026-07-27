package com.reconsiliation.caisse.sms

data class ParsedSms(
    val amount: Double,
    val transactionId: String,
    val sender: String,
    val type: String, // MOMO_MTN, MOMO_ORANGE, SUB_CONFIRMATION
    val receiver: String? = null,
    val isSecure: Boolean = false // Point 1: Anti-fraud flag
)

object SmsParser {
    private val OFFICIAL_SENDERS = listOf("mobilemoney", "mtnmomo", "orangemoney", "om", "6700", "momo")
    private val KEYWORDS_SUCCESS = listOf("recu", "reçu", "succes", "succès", "confirme", "confirmé", "effectue", "effectué", "valide", "validé")
    private val CURRENCIES = listOf("fcfa", "fcf", "xaf", " fcfa", " f")

    fun parse(body: String, originatingAddress: String? = null): ParsedSms? {
        // 1. STERILIZATION Phase: Remove noise but keep structure
        val cleanBody = body.lowercase()
            .replace(Regex("[^a-z0-9\\sàâäéèêëîïôöùûüç°.,]"), " ") // Added . and ,
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val senderAddr = originatingAddress?.lowercase() ?: ""
        val isOfficial = OFFICIAL_SENDERS.any { senderAddr.contains(it) } || (senderAddr.length <= 6 && senderAddr.isNotEmpty())

        // 2. SCORING Phase: Determine if it's a payment message
        var score = 0
        if (isOfficial) score += 40
        KEYWORDS_SUCCESS.forEach { if (cleanBody.contains(it)) score += 15 }
        CURRENCIES.forEach { if (cleanBody.contains(it)) score += 10 }

        // High threshold for surgical precision
        if (score < 50) return null

        // 3. SURGICAL EXTRACTION - Amount
        // Rule: A number followed by a currency indicator, preceded by success keywords
        var amount = 0.0
        val amountPatterns = listOf(
            Regex("(\\d{2,})\\s*(?:fcfa|fcf|xaf|f)\\b"), // Standard: 5000 FCFA
            Regex("(?:montant|somme|valeur)[:\\s]*(\\d{2,})") // Fallback: Montant: 5000
        )
        
        for (pattern in amountPatterns) {
            val match = pattern.find(cleanBody)
            if (match != null) {
                amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) break
            }
        }

        // 4. SURGICAL EXTRACTION - Transaction ID
        // Rule: Long alphanumeric string (8+ chars) that isn't a phone number
        // We prioritize strings next to ID/REF keywords
        val idKeywordsPattern = Regex("(?:id|ref|transaction|no|n°|reference)[:\\s]*([a-z0-9]{8,})")
        val idMatch = idKeywordsPattern.find(cleanBody)
        
        val transactionId = if (idMatch != null) {
            idMatch.groupValues[1].uppercase()
        } else {
            // Fallback: Look for any isolated long alphanumeric string that isn't 9 digits starting with 6
            val words = cleanBody.split(" ")
            words.find { word ->
                word.length >= 10 && !word.matches(Regex("^6[25-9]\\d{7}$")) && word.matches(Regex(".*[a-z0-9].*"))
            }?.uppercase() ?: ""
        }

        // 5. SURGICAL EXTRACTION - Sender Phone
        // Rule: 9 digit number starting with 6 (Cameroon)
        var senderPhone = ""
        val phonePattern = Regex("(?:de|from|par|sender)[:\\s]*(?:237)?(6[25-9]\\d{7})")
        val phoneMatch = phonePattern.find(cleanBody)
        
        if (phoneMatch != null) {
            senderPhone = phoneMatch.groupValues[1]
        } else {
            // Fallback: search for any 9-digit sequence starting with 6 that isn't the transactionId
            val allPhones = Regex("\\b6[25-9]\\d{7}\\b").findAll(cleanBody)
            senderPhone = allPhones.map { it.value }.firstOrNull { it != transactionId } ?: ""
        }

        // 6. SPECIAL CASE - Subscription Detection (692971991)
        val devNumber = "692971991"
        val type = when {
            cleanBody.contains(devNumber) || cleanBody.contains("reconciliation") -> "SUB_CONFIRMATION"
            senderAddr.contains("mtn") || cleanBody.contains("mtn") -> "MOMO_MTN"
            senderAddr.contains("orange") || cleanBody.contains("orange") || senderAddr.contains("om") -> "MOMO_ORANGE"
            else -> "MOMO_MTN"
        }

        // 7. FINAL SURGICAL VALIDATION
        // We only accept if we have BOTH Amount AND Transaction ID
        if (amount > 0 && transactionId.isNotBlank()) {
            return ParsedSms(
                amount = amount,
                transactionId = transactionId,
                sender = senderPhone,
                type = type,
                isSecure = isOfficial,
                receiver = if (type == "SUB_CONFIRMATION") devNumber else null
            )
        }

        return null
    }
}
