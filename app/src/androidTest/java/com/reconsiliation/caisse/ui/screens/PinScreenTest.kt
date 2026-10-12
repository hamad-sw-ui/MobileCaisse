package com.reconsiliation.caisse.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reconsiliation.caisse.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val pinRequiredText: String
        get() = targetContext.getString(R.string.pin_required)

    private val pinEnterDescText: String
        get() = targetContext.getString(R.string.pin_enter_desc)

    private val pinInvalidText: String
        get() = targetContext.getString(R.string.pin_invalid)

    private val deleteContentDescription: String
        get() = targetContext.getString(R.string.action_delete)

    @Test
    fun handlePinKeyPress_productionFunction_cappingLogic() {
        // Unit test verifying production function handlePinKeyPress directly
        assertEquals("1", handlePinKeyPress("", "1"))
        assertEquals("12", handlePinKeyPress("1", "2"))
        assertEquals("123", handlePinKeyPress("12", "3"))
        assertEquals("1234", handlePinKeyPress("123", "4"))
        // 5th digit must be ignored by production function
        assertEquals("1234", handlePinKeyPress("1234", "5"))
        // DEL key test
        assertEquals("123", handlePinKeyPress("1234", "DEL"))
    }

    @Test
    fun pinContent_displaysInitialUi() {
        composeTestRule.setContent {
            PinContent(
                pin = "",
                error = false,
                onKeyPress = {}
            )
        }

        // Verify title & description are displayed
        composeTestRule.onNodeWithText(pinRequiredText).assertIsDisplayed()
        composeTestRule.onNodeWithText(pinEnterDescText).assertIsDisplayed()

        // Verify error message is NOT displayed
        composeTestRule.onNodeWithText(pinInvalidText).assertDoesNotExist()

        // Verify all 4 PIN dots are displayed
        for (index in 0..3) {
            composeTestRule.onNodeWithTag("pin_dot_$index").assertIsDisplayed()
        }

        // Verify digits 0..9 and delete button exist
        for (digit in 0..9) {
            composeTestRule.onNodeWithText(digit.toString()).assertExists()
        }
        composeTestRule.onNodeWithContentDescription(deleteContentDescription).assertExists()
    }

    @Test
    fun pinContent_displaysPinDots_forEmptyPartialAndFullPin() {
        // Test empty PIN
        composeTestRule.setContent {
            PinContent(pin = "", error = false, onKeyPress = {})
        }
        for (index in 0..3) {
            composeTestRule.onNodeWithTag("pin_dot_$index").assertIsDisplayed()
        }

        // Test partial PIN (2 digits)
        composeTestRule.setContent {
            PinContent(pin = "12", error = false, onKeyPress = {})
        }
        for (index in 0..3) {
            composeTestRule.onNodeWithTag("pin_dot_$index").assertIsDisplayed()
        }

        // Test full PIN (4 digits)
        composeTestRule.setContent {
            PinContent(pin = "1234", error = false, onKeyPress = {})
        }
        for (index in 0..3) {
            composeTestRule.onNodeWithTag("pin_dot_$index").assertIsDisplayed()
        }
    }

    @Test
    fun pinContent_triggersOnKeyPress_whenDigitPressed() {
        var pressedKey = ""

        composeTestRule.setContent {
            PinContent(
                pin = "12",
                error = false,
                onKeyPress = { key -> pressedKey = key }
            )
        }

        composeTestRule.onNodeWithText("5").performClick()
        assertEquals("5", pressedKey)
    }

    @Test
    fun pinContent_triggersOnKeyPress_whenDeletePressed() {
        var pressedKey = ""

        composeTestRule.setContent {
            PinContent(
                pin = "12",
                error = false,
                onKeyPress = { key -> pressedKey = key }
            )
        }

        composeTestRule.onNodeWithContentDescription(deleteContentDescription).performClick()
        assertEquals("DEL", pressedKey)
    }

    @Test
    fun pinContent_displaysError_whenErrorStateIsTrue() {
        composeTestRule.setContent {
            PinContent(
                pin = "",
                error = true,
                onKeyPress = {}
            )
        }

        // Verify error message IS displayed
        composeTestRule.onNodeWithText(pinInvalidText).assertIsDisplayed()

        // Verify all 4 PIN dots are displayed in error state
        for (index in 0..3) {
            composeTestRule.onNodeWithTag("pin_dot_$index").assertIsDisplayed()
        }
    }

    @Test
    fun pinContent_enforcesMaxFourDigitsLimit_usingProductionFunction() {
        var currentPinLength = 0

        composeTestRule.setContent {
            StatefulPinHarness(
                onPinLengthChanged = { length -> currentPinLength = length }
            )
        }

        // Press 5 digits sequentially: 1, 2, 3, 4, 5
        composeTestRule.onNodeWithText("1").performClick()
        assertEquals(1, currentPinLength)

        composeTestRule.onNodeWithText("2").performClick()
        assertEquals(2, currentPinLength)

        composeTestRule.onNodeWithText("3").performClick()
        assertEquals(3, currentPinLength)

        composeTestRule.onNodeWithText("4").performClick()
        assertEquals(4, currentPinLength)

        // 5th keypress must NOT increase length beyond 4 (uses production handlePinKeyPress)
        composeTestRule.onNodeWithText("5").performClick()
        assertEquals(4, currentPinLength)
    }

    @Composable
    private fun StatefulPinHarness(
        onPinLengthChanged: (Int) -> Unit
    ) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        PinContent(
            pin = pin,
            error = error,
            onKeyPress = { key ->
                error = false
                pin = handlePinKeyPress(pin, key)
                onPinLengthChanged(pin.length)
            }
        )
    }
}
