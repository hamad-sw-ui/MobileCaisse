package com.reconsiliation.caisse.permissions

import com.reconsiliation.caisse.ui.viewmodel.SmsCatchUpState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun `granted permission never asks again`() {
        assertEquals(
            PermissionDecision.Granted,
            PermissionPolicy.decide(granted = true, rationaleVisible = false, requestAttempted = true)
        )
    }

    @Test
    fun `first denial path requests the system dialog`() {
        assertEquals(
            PermissionDecision.Request,
            PermissionPolicy.decide(granted = false, rationaleVisible = false, requestAttempted = false)
        )
    }

    @Test
    fun `simple denial with rationale can be requested again`() {
        assertEquals(
            PermissionDecision.Request,
            PermissionPolicy.decide(granted = false, rationaleVisible = true, requestAttempted = true)
        )
    }

    @Test
    fun `permanent denial opens settings`() {
        assertEquals(
            PermissionDecision.OpenSettings,
            PermissionPolicy.decide(granted = false, rationaleVisible = false, requestAttempted = true)
        )
    }

    @Test
    fun `bluetooth connect is required only from API 31`() {
        assertFalse(PermissionPolicy.needsBluetoothConnect(30))
        assertTrue(PermissionPolicy.needsBluetoothConnect(31))
        assertNull(PermissionPolicy.bluetoothConnectPermission(30))
        assertEquals(PermissionPolicy.BLUETOOTH_CONNECT, PermissionPolicy.bluetoothConnectPermission(31))
    }

    @Test
    fun `notifications are required only from API 33`() {
        assertFalse(PermissionPolicy.needsNotifications(32))
        assertTrue(PermissionPolicy.needsNotifications(33))
        assertNull(PermissionPolicy.notificationPermission(32))
        assertEquals(PermissionPolicy.POST_NOTIFICATIONS, PermissionPolicy.notificationPermission(33))
    }

    @Test
    fun `bluetooth scan is not required for paired printers`() {
        assertFalse(PermissionPolicy.needsBluetoothScan())
    }

    @Test
    fun `security exception maps to sms permission denied`() {
        assertEquals(SmsSyncResult.PermissionDenied, SmsSyncOutcome.fromThrowable(SecurityException("denied")))
    }

    @Test
    fun `other sms failures stay failed`() {
        val result = SmsSyncOutcome.fromThrowable(IllegalStateException("inbox"))
        assertTrue(result is SmsSyncResult.Failed)
    }

    @Test
    fun `bluetooth remains usable below API 31 without connect grant`() {
        assertTrue(PermissionPolicy.canUseBluetooth(30, connectGranted = false))
        assertFalse(PermissionPolicy.canUseBluetooth(31, connectGranted = false))
        assertTrue(PermissionPolicy.canUseBluetooth(31, connectGranted = true))
    }

    @Test
    fun `camera denial never looks granted so manual entry stays available`() {
        assertEquals(
            PermissionDecision.Request,
            PermissionPolicy.decide(granted = false, rationaleVisible = true, requestAttempted = true)
        )
        assertEquals(
            PermissionDecision.OpenSettings,
            PermissionPolicy.decide(granted = false, rationaleVisible = false, requestAttempted = true)
        )
    }

    @Test
    fun `silent sms catch-up does not surface success or failure`() {
        assertEquals(SmsCatchUpState.Idle, SmsCatchUpState.from(SmsSyncResult.Success(3), userInitiated = false))
        assertEquals(SmsCatchUpState.Idle, SmsCatchUpState.from(SmsSyncResult.Failed("inbox"), userInitiated = false))
        assertEquals(SmsCatchUpState.PermissionRequired, SmsCatchUpState.from(SmsSyncResult.PermissionDenied, userInitiated = false))
    }

    @Test
    fun `user initiated sms catch-up exposes usable outcomes`() {
        assertEquals(SmsCatchUpState.Completed(2), SmsCatchUpState.from(SmsSyncResult.Success(2), userInitiated = true))
        assertEquals(SmsCatchUpState.Failed, SmsCatchUpState.from(SmsSyncResult.Failed("inbox"), userInitiated = true))
        assertEquals(SmsCatchUpState.PermissionRequired, SmsCatchUpState.from(SmsSyncResult.PermissionDenied, userInitiated = true))
    }
}
