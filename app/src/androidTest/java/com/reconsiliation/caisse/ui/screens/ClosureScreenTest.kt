package com.reconsiliation.caisse.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.repository.SessionPreview
import com.reconsiliation.caisse.ui.state.SessionUiState
import com.reconsiliation.caisse.utils.FormatUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ClosureScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val noActivityText: String
        get() = targetContext.getString(R.string.error_no_activity_today)

    private val makeSaleText: String
        get() = targetContext.getString(R.string.action_make_sale)

    private val cashValidationText: String
        get() = targetContext.getString(R.string.closure_cash_validation)

    private val statusDayClosedText: String
        get() = targetContext.getString(R.string.status_day_closed)

    private val labelDiffText: String
        get() = targetContext.getString(R.string.label_diff)

    private fun createVente(id: Long = 1L) = VenteEntity(
        id = id,
        amount = 10000.0,
        description = "Vente test",
        date = Date(),
        paymentMethod = "CASH",
        status = "CONFIRMED"
    )

    @Test
    fun closureContent_displaysNoActivityState_whenTodayVentesIsEmpty() {
        composeTestRule.setContent {
            ClosureContent(
                todayVentes = emptyList(),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = false,
                sessionPreview = null,
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = {},
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        // Verify empty activity container tag and texts
        composeTestRule.onNodeWithTag("closure_empty_view").assertIsDisplayed()
        composeTestRule.onNodeWithText(noActivityText).assertIsDisplayed()
        composeTestRule.onNodeWithText(makeSaleText).assertIsDisplayed()
    }

    @Test
    fun closureContent_displaysSummary_whenTodayVentesIsNotEmpty() {
        val vente = createVente()

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 5000.0,
                creditTotal = 2000.0,
                isLocked = false,
                sessionPreview = SessionPreview(
                    openingBalance = 5000.0,
                    cashSales = 10000.0,
                    expenses = 1000.0,
                    expectedCash = 14000.0
                ),
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = {},
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        // Verify summary container tag and cash validation text
        composeTestRule.onNodeWithTag("closure_summary_content").assertIsDisplayed()
        composeTestRule.onNodeWithText(cashValidationText).assertIsDisplayed()
        composeTestRule.onNodeWithTag("closure_cash_input").assertIsDisplayed()
    }

    @Test
    fun closureContent_triggersExportCsvCallback_whenExportClicked() {
        val vente = createVente()
        var exportClicked = false

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = false,
                sessionPreview = null,
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = { exportClicked = true },
                onTotalBackupClick = {},
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("btn_export_csv").performClick()
        assertTrue(exportClicked)
    }

    @Test
    fun closureContent_triggersTotalBackupCallback_whenBackupClicked() {
        val vente = createVente()
        var backupClicked = false

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = false,
                sessionPreview = null,
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = { backupClicked = true },
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("btn_total_backup").performClick()
        assertTrue(backupClicked)
    }

    @Test
    fun closureContent_displaysLockedCard_whenIsLockedIsTrue() {
        val vente = createVente()

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = true,
                sessionPreview = null,
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = {},
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        // Verify locked card is displayed and close button does NOT exist
        composeTestRule.onNodeWithTag("closure_locked_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(statusDayClosedText).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_close_and_lock").assertDoesNotExist()
    }

    @Test
    fun closureContent_triggersCloseSessionCallback_withCountedCash() {
        val vente = createVente()
        var submittedCountedCash = -1.0

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = false,
                sessionPreview = SessionPreview(5000.0, 10000.0, 0.0, 15000.0),
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = {},
                onCloseSessionSubmit = { counted -> submittedCountedCash = counted },
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        // Enter counted cash
        composeTestRule.onNodeWithTag("closure_cash_input").performTextInput("15000")

        // Click close and lock button
        composeTestRule.onNodeWithTag("btn_close_and_lock").performClick()

        assertEquals(15000.0, submittedCountedCash, 0.01)
    }

    @Test
    fun closureContent_updatesCashGap_whenCountedCashChanges() {
        val vente = createVente()
        val expectedCash = 15000.0
        val countedCash = 12000.0
        val expectedGap = countedCash - expectedCash // -3000.0
        val expectedGapFormatted = FormatUtil.formatCurrency(expectedGap)

        composeTestRule.setContent {
            ClosureContent(
                todayVentes = listOf(vente),
                allVentesWithItems = emptyList(),
                momoTotal = 0.0,
                creditTotal = 0.0,
                isLocked = false,
                sessionPreview = SessionPreview(5000.0, 10000.0, 0.0, expectedCash),
                operatorName = "MoMo",
                sessionUiState = SessionUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onExportSalesCsvClick = {},
                onTotalBackupClick = {},
                onCloseSessionSubmit = {},
                onNavigateToNewSale = {},
                onNavigateBack = {}
            )
        }

        // Enter counted cash "12000" (gap = -3000.0)
        composeTestRule.onNodeWithTag("closure_cash_input").performTextInput("12000")

        // Verify diff label is displayed
        composeTestRule.onNodeWithText(labelDiffText).assertIsDisplayed()

        // Verify formatted cash gap value "-3 000 FCFA" is displayed
        composeTestRule.onNodeWithText(expectedGapFormatted).assertIsDisplayed()
    }
}
