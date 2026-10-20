package com.reconsiliation.caisse.sms

import org.junit.Assert.*
import org.junit.Test

class SmsParserSecurityTest {

    @Test
    fun `short address of 5 characters should NOT be considered official`() {
        val body = "Transfert de 1000 FCFA effectue avec succes. ID: SEC5CHARS1."
        val parsed = SmsParser.parse(body, originatingAddress = "12345")

        assertNotNull("SMS should be parsed", parsed)
        assertFalse("Address with 5 chars must not be official", parsed!!.isSecure)
    }

    @Test
    fun `address containing mobilemoney should be considered official`() {
        val body = "Transfert de 5000 FCFA effectue avec succes. ID: OFFMOB1234."
        val parsed = SmsParser.parse(body, originatingAddress = "mobilemoney")

        assertNotNull("SMS should be parsed", parsed)
        assertTrue("mobilemoney address must be official", parsed!!.isSecure)
    }

    @Test
    fun `address with 6 characters containing official keyword is still official`() {
        val body = "Recu de 2500 FCFA. Transaction ID: SIXCHAR12."
        val parsed = SmsParser.parse(body, originatingAddress = "momo67")

        assertNotNull("SMS should be parsed", parsed)
        assertTrue("Address containing momo must be official", parsed!!.isSecure)
    }
}
