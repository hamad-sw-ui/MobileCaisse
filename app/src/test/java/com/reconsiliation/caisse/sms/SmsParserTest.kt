package com.reconsiliation.caisse.sms

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    @Test
    fun `parse standard MTN MoMo SMS should return correct data`() {
        val body = "Transfert de 5000 FCFA effectue avec succes vers 677112233. ID: 2598877661. Nouveau solde: 15000 FCFA."
        val sender = "MobileMoney"
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNotNull("SMS should be parsed", parsed)
        assertEquals(5000.0, parsed!!.amount, 0.0)
        assertEquals("2598877661", parsed.transactionId)
        assertTrue("Should be marked as secure", parsed.isSecure)
        assertEquals("MOMO_MTN", parsed.type)
    }

    @Test
    fun `parse standard Orange Money SMS should return correct data`() {
        val body = "Vous avez recu 10000 FCFA de 699887766. Transaction ID: MP260522.1234.B56789. Votre nouveau solde est de 25000 FCFA."
        val sender = "OrangeMoney"
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNotNull("SMS should be parsed", parsed)
        assertEquals(10000.0, parsed!!.amount, 0.0)
        assertEquals("MP260522.1234.B56789", parsed.transactionId)
        assertEquals("699887766", parsed.sender)
        assertTrue("Should be marked as secure", parsed.isSecure)
        assertEquals("MOMO_ORANGE", parsed.type)
    }

    @Test
    fun `parse subscription SMS should detect SUB_CONFIRMATION type`() {
        val body = "Paiement de 5000 FCFA effectue vers RECONCILIATION APP (692971991). Transaction ID: SUB123456789."
        val sender = "MobileMoney"
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNotNull("Subscription SMS should be parsed", parsed)
        assertEquals("SUB_CONFIRMATION", parsed!!.type)
        assertEquals("692971991", parsed.receiver)
    }

    @Test
    fun `parse SMS from unofficial sender should mark isSecure as false`() {
        val body = "Transfert de 2000 FCFA effectue. ID: FAKEID123456."
        val sender = "671234567" // A normal phone number
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNotNull(parsed)
        assertFalse("Should NOT be marked as secure from unofficial sender", parsed!!.isSecure)
    }

    @Test
    fun `non-payment SMS should be ignored`() {
        val body = "Votre solde est de 1500 FCFA. Tapez *126# pour plus d'infos."
        val sender = "MTNMoMo"
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNull("Non-payment SMS should return null", parsed)
    }

    @Test
    fun `SMS with XAF currency should be parsed`() {
        val body = "Recu 1500 XAF. Trans ID: XAF987654321."
        val sender = "momo"
        
        val parsed = SmsParser.parse(body, sender)
        
        assertNotNull(parsed)
        assertEquals(1500.0, parsed!!.amount, 0.0)
        assertEquals("XAF987654321", parsed.transactionId)
    }

    @Test
    fun `SMS with comma as decimal separator should handle amount correctly`() {
        val body = "Transfert de 1500,50 FCFA effectue. ID: DEC12345678."
        val parsed = SmsParser.parse(body, "momo")
        
        assertNotNull(parsed)
        assertEquals(1500.5, parsed!!.amount, 0.0)
    }
}
