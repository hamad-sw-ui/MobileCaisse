package com.reconsiliation.caisse.utils

object PhoneUtil {
    fun normalize(phone: String?): String {
        if (phone == null) return ""
        // Remove all non-digits
        val digits = phone.replace(Regex("\\D"), "")
        // If it starts with 225 (CI) and has 13 digits (00225...), take last 10
        // Adjust this logic based on your specific country needs
        return if (digits.length >= 10) {
            digits.takeLast(10)
        } else {
            digits
        }
    }

    fun matches(p1: String?, p2: String?): Boolean {
        if (p1.isNullOrBlank() || p2.isNullOrBlank()) return false
        return normalize(p1) == normalize(p2)
    }
}
