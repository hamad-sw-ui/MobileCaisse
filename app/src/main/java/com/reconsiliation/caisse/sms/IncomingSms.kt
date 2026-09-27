package com.reconsiliation.caisse.sms

data class IncomingSms(
    val body: String,
    val sender: String?,
    val timestampMillis: Long?
)
