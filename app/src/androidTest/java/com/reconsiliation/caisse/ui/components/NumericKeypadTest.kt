package com.reconsiliation.caisse.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reconsiliation.caisse.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumericKeypadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val deleteContentDescription: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_delete)

    @Test
    fun numericKeypad_displaysAllKeys() {
        composeTestRule.setContent {
            NumericKeypad(
                onNumberClick = {},
                onDeleteClick = {}
            )
        }

        // Verify digits 0 through 9 are displayed
        for (digit in 0..9) {
            composeTestRule.onNodeWithText(digit.toString()).assertExists()
        }

        // Verify backspace action is displayed via content description
        composeTestRule.onNodeWithContentDescription(deleteContentDescription).assertExists()
    }

    @Test
    fun numericKeypad_triggersOnNumberClick_whenDigitPressed() {
        var clickedNumber = ""

        composeTestRule.setContent {
            NumericKeypad(
                onNumberClick = { number -> clickedNumber = number },
                onDeleteClick = {}
            )
        }

        composeTestRule.onNodeWithText("5").performClick()
        assertEquals("5", clickedNumber)

        composeTestRule.onNodeWithText("8").performClick()
        assertEquals("8", clickedNumber)
    }

    @Test
    fun numericKeypad_triggersOnDeleteClick_whenDeletePressed() {
        var deleteClicked = false

        composeTestRule.setContent {
            NumericKeypad(
                onNumberClick = {},
                onDeleteClick = { deleteClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription(deleteContentDescription).performClick()
        assertTrue(deleteClicked)
    }
}
